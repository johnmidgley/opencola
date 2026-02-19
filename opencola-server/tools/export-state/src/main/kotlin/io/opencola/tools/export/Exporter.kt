package io.opencola.tools.export

import io.opencola.model.*
import io.opencola.model.value.*
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val logger = KotlinLogging.logger("Exporter")

data class ExportResult(
    val outputPath: String,
    val transactionCount: Int,
    val dataFileCount: Int,
    val errors: List<String>
)

/**
 * Exports transactions and data files for a given authority as a zip archive
 * compatible with the Rust OpenCola importer.
 *
 * Archive structure:
 * ```
 * opencola-export-{name}-{date}/
 * ├── export-manifest.json
 * ├── transactions.json
 * └── data/
 *     ├── Ab/cdef1234...
 *     └── 7f/9a8b7c6d...
 * ```
 */
class Exporter(private val storage: StorageAccess) {

    private val buildVersion: String? by lazy {
        try {
            val props = java.util.Properties()
            this::class.java.classLoader.getResourceAsStream("version.properties")?.use { props.load(it) }
            props.getProperty("opencola.version")
        } catch (e: Exception) {
            null
        }
    }

    fun export(
        authorityId: Id,
        authorityName: String,
        outputDir: Path,
        transactionIds: Set<Id>? = null // null = export all
    ): ExportResult {
        val errors = mutableListOf<String>()

        // Get transactions
        val allTransactions = storage.entityStore.getSignedTransactions(
            setOf(authorityId),
            null,
            TransactionOrder.IdAscending,
            Int.MAX_VALUE
        ).toList()

        val transactions = if (transactionIds != null) {
            allTransactions.filter { it.transaction.id in transactionIds }
        } else {
            allTransactions
        }

        logger.info { "Exporting ${transactions.size} transactions for $authorityName ($authorityId)" }

        // Convert transactions to JSON
        val transactionsJson = buildJsonArray {
            for (st in transactions) {
                add(signedTransactionToJson(st))
            }
        }
        val transactionsBytes = Json { prettyPrint = true }
            .encodeToString(JsonArray.serializer(), transactionsJson)
            .toByteArray(Charsets.UTF_8)

        // Collect data files
        val dataIds = collectDataIds(transactions)
        val dataFiles = mutableMapOf<String, ByteArray>()
        for (dataId in dataIds) {
            try {
                val data = storage.contentFileStore.read(dataId)
                if (data != null) {
                    dataFiles[dataId.toString()] = data
                } else {
                    errors.add("Data file not found for ID: $dataId")
                }
            } catch (e: Exception) {
                errors.add("Error reading data file $dataId: ${e.message}")
            }
        }

        // Get public key for manifest
        val publicKey: PublicKey? = try {
            storage.getPublicKey(authorityId)
        } catch (e: Exception) {
            errors.add("Could not retrieve public key: ${e.message}")
            null
        }

        // Build manifest
        val manifest = buildJsonObject {
            put("export_version", 1)
            put("source", "opencola-kotlin")
            if (buildVersion != null) put("opencola_version", buildVersion)
            put("exported_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)))
            putJsonObject("authority") {
                put("id", authorityId.toString())
                put("name", authorityName)
                if (publicKey != null) {
                    put("public_key", publicKey.encoded.toHexString())
                }
            }
            put("transaction_count", transactions.size)
            put("data_file_count", dataFiles.size)
            putJsonArray("data_ids") {
                dataIds.forEach { add(it.toString()) }
            }
            if (errors.isNotEmpty()) {
                putJsonArray("errors") {
                    errors.forEach { add(it) }
                }
            }
        }
        val manifestBytes = Json { prettyPrint = true }
            .encodeToString(JsonObject.serializer(), manifest)
            .toByteArray(Charsets.UTF_8)

        // Build zip archive matching Rust export format
        val safeName = authorityName.map { c ->
            if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_'
        }.joinToString("")
        val dateStr = LocalDate.now().toString() // yyyy-MM-dd
        val dirPrefix = "opencola-export-$safeName-$dateStr"

        outputDir.toFile().mkdirs()
        val zipFile = outputDir.resolve("$dirPrefix.zip").toFile()

        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)

            // Write export-manifest.json
            zip.putNextEntry(ZipEntry("$dirPrefix/export-manifest.json"))
            zip.write(manifestBytes)
            zip.closeEntry()

            // Write transactions.json
            zip.putNextEntry(ZipEntry("$dirPrefix/transactions.json"))
            zip.write(transactionsBytes)
            zip.closeEntry()

            // Write data files with 2-char prefix directory structure (stored, not compressed)
            for ((idStr, data) in dataFiles) {
                val prefix = if (idStr.length >= 2) idStr.substring(0, 2) else idStr
                val entry = ZipEntry("$dirPrefix/data/$prefix/$idStr")
                entry.method = ZipEntry.STORED
                entry.size = data.size.toLong()
                entry.compressedSize = data.size.toLong()
                entry.crc = java.util.zip.CRC32().also { it.update(data) }.value
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }

        logger.info { "Export complete: ${transactions.size} transactions, ${dataFiles.size} data files to $zipFile" }

        return ExportResult(
            outputPath = zipFile.absolutePath,
            transactionCount = transactions.size,
            dataFileCount = dataFiles.size,
            errors = errors
        )
    }

    private fun signedTransactionToJson(st: SignedTransaction): JsonObject {
        val tx = st.transaction
        return buildJsonObject {
            put("id", tx.id.toString())
            put("authority_id", tx.authorityId.toString())
            put("epoch_second", tx.epochSecond)
            put("timestamp", DateTimeFormatter.ISO_INSTANT.format(
                Instant.ofEpochSecond(tx.epochSecond).atOffset(ZoneOffset.UTC)
            ))
            put("signature", st.signature.bytes.toHexString())
            put("signature_algorithm", st.signature.algorithm.algorithmName)
            putJsonArray("entities") {
                for (entity in tx.transactionEntities) {
                    add(transactionEntityToJson(entity))
                }
            }
        }
    }

    private fun transactionEntityToJson(entity: TransactionEntity): JsonObject {
        return buildJsonObject {
            put("entity_id", entity.entityId.toString())
            putJsonArray("facts") {
                for (fact in entity.facts) {
                    add(transactionFactToJson(fact))
                }
            }
        }
    }

    private fun transactionFactToJson(fact: TransactionFact): JsonObject {
        return buildJsonObject {
            put("attribute", fact.attribute.uri.toString())
            put("value", valueToJson(fact.value))
            put("operation", fact.operation.name)
        }
    }

    /**
     * Converts a Kotlin Value to JSON matching the Rust Value enum serde format:
     * { "type": "String", "value": "..." }
     *
     * Uses the runtime type of the underlying value since Value<Any> erases
     * the generic type parameter, making `is StringValue` checks impossible.
     */
    private fun valueToJson(value: Value<Any>): JsonObject {
        if (value === EmptyValue) {
            return buildJsonObject { put("type", "Empty") }
        }

        val raw = value.get()
        return when (raw) {
            is String -> buildJsonObject {
                put("type", "String")
                put("value", raw)
            }
            is Boolean -> buildJsonObject {
                put("type", "Boolean")
                put("value", raw)
            }
            is Int -> buildJsonObject {
                put("type", "Int")
                put("value", raw.toLong())
            }
            is Float -> buildJsonObject {
                put("type", "Float")
                put("value", raw.toDouble())
            }
            is URI -> buildJsonObject {
                put("type", "Uri")
                put("value", raw.toString())
            }
            is Id -> buildJsonObject {
                put("type", "Id")
                put("value", raw.toString())
            }
            is PublicKey -> buildJsonObject {
                put("type", "PublicKey")
                put("value", raw.encoded.toHexString())
            }
            is ByteArray -> buildJsonObject {
                put("type", "ByteArray")
                put("value", raw.toHexString())
            }
            else -> buildJsonObject {
                put("type", "String")
                put("value", raw.toString())
            }
        }
    }

    private fun collectDataIds(transactions: List<SignedTransaction>): Set<Id> {
        val dataIds = mutableSetOf<Id>()
        for (st in transactions) {
            for (entity in st.transaction.transactionEntities) {
                for (fact in entity.facts) {
                    if (fact.attribute.name == CoreAttribute.DataIds.spec.name && fact.operation == Operation.Add) {
                        val id = fact.value.get()
                        if (id is Id) {
                            dataIds.add(id)
                        }
                    }
                }
            }
        }
        return dataIds
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
