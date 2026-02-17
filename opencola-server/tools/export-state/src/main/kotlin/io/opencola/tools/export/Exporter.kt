package io.opencola.tools.export

import io.opencola.model.*
import io.opencola.model.value.*
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.PublicKey
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger("Exporter")

data class ExportResult(
    val outputPath: String,
    val transactionCount: Int,
    val dataFileCount: Int,
    val errors: List<String>
)

/**
 * Exports transactions and data files for a given authority in a neutral JSON format
 * compatible with the Rust OpenCola Value enum serde format.
 */
class Exporter(private val storage: StorageAccess) {

    fun export(
        authorityId: Id,
        authorityName: String,
        outputDir: Path,
        transactionIds: Set<Id>? = null // null = export all
    ): ExportResult {
        val errors = mutableListOf<String>()

        // Create output directories
        outputDir.toFile().mkdirs()
        val dataDir = outputDir.resolve("data")
        dataDir.toFile().mkdirs()

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

        // Write transactions.json
        val transactionsFile = outputDir.resolve("transactions.json").toFile()
        transactionsFile.writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), transactionsJson))

        // Collect and copy data files
        val dataIds = collectDataIds(transactions)
        var dataFileCount = 0
        for (dataId in dataIds) {
            try {
                val data = storage.contentFileStore.read(dataId)
                if (data != null) {
                    val idStr = dataId.toString()
                    val prefix = idStr.substring(0, 2)
                    val prefixDir = dataDir.resolve(prefix)
                    prefixDir.toFile().mkdirs()
                    val dataFile = prefixDir.resolve(idStr.substring(2))
                    dataFile.toFile().writeBytes(data)
                    dataFileCount++
                } else {
                    errors.add("Data file not found for ID: $dataId")
                }
            } catch (e: Exception) {
                errors.add("Error copying data file $dataId: ${e.message}")
            }
        }

        // Get public key for manifest
        val publicKey: PublicKey? = try {
            storage.getPublicKey(authorityId)
        } catch (e: Exception) {
            errors.add("Could not retrieve public key: ${e.message}")
            null
        }

        // Write export-manifest.json
        val manifest = buildJsonObject {
            put("export_version", 1)
            put("source", "opencola-kotlin")
            put("exported_at", DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC)))
            putJsonObject("authority") {
                put("id", authorityId.toString())
                put("name", authorityName)
                if (publicKey != null) {
                    put("public_key", publicKey.encoded.toHexString())
                }
            }
            put("transaction_count", transactions.size)
            put("data_file_count", dataFileCount)
            if (errors.isNotEmpty()) {
                putJsonArray("errors") {
                    errors.forEach { add(it) }
                }
            }
        }

        val manifestFile = outputDir.resolve("export-manifest.json").toFile()
        manifestFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), manifest))

        logger.info { "Export complete: ${transactions.size} transactions, $dataFileCount data files to $outputDir" }

        return ExportResult(
            outputPath = outputDir.toString(),
            transactionCount = transactions.size,
            dataFileCount = dataFileCount,
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
            put("attribute", fact.attribute.name)
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
