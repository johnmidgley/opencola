package io.opencola.tools.export

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.opencola.model.*
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import kotlinx.serialization.json.*
import mu.KotlinLogging
import java.nio.file.Path
import java.nio.file.Paths

private val logger = KotlinLogging.logger("ExportServer")

class ExportServer(
    private val storage: StorageAccess,
    private val port: Int = 8090,
    private val defaultOutputDir: Path = Paths.get("export")
) {
    private val chainVerifier = ChainVerifier(storage)
    private val exporter = Exporter(storage)
    private val orphanAnalyzer = OrphanAnalyzer(storage)

    fun start(openBrowser: Boolean = false) {
        logger.info { "Starting export server on port $port" }
        val server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json { prettyPrint = true })
            }
            configureRoutes()
        }.start(wait = false)

        if (openBrowser) {
            openBrowser("http://localhost:$port")
        }

        // Block until the server is stopped
        Thread.currentThread().join()
    }

    private fun openBrowser(url: String) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> ProcessBuilder("open", url).start()
                os.contains("win") -> ProcessBuilder("cmd", "/c", "start", url).start()
                else -> ProcessBuilder("xdg-open", url).start()
            }
            logger.info { "Opened browser to $url" }
        } catch (e: Exception) {
            logger.warn { "Could not open browser: ${e.message}. Please navigate to $url manually." }
        }
    }

    private fun Application.configureRoutes() {
        routing {
            // Serve web UI
            get("/") {
                val html = this::class.java.classLoader.getResource("web/index.html")?.readText()
                if (html != null) {
                    call.respondText(html, ContentType.Text.Html)
                } else {
                    call.respondText("Web UI not found", status = HttpStatusCode.NotFound)
                }
            }

            // API routes
            route("/api") {
                get("/personas") {
                    val personas = storage.getPersonas().map { entryToJson(it, isPersona = true) }
                    call.respond(buildJsonObject {
                        putJsonArray("personas") { personas.forEach { add(it) } }
                    })
                }

                get("/peers") {
                    val peers = storage.getPeers().map { entryToJson(it, isPersona = false) }
                    call.respond(buildJsonObject {
                        putJsonArray("peers") { peers.forEach { add(it) } }
                    })
                }

                get("/authority/{id}/verify") {
                    val idStr = call.parameters["id"] ?: return@get call.respondText(
                        "Missing authority ID", status = HttpStatusCode.BadRequest
                    )
                    val authorityId = try {
                        Id.decode(idStr)
                    } catch (e: Exception) {
                        return@get call.respondText(
                            "Invalid authority ID: ${e.message}", status = HttpStatusCode.BadRequest
                        )
                    }

                    val name = findAuthorityName(authorityId)
                    val result = chainVerifier.verify(authorityId, name)

                    call.respond(buildJsonObject {
                        put("authority_id", result.authorityId)
                        put("authority_name", result.authorityName)
                        put("total_transactions", result.totalTransactions)
                        put("valid_signatures", result.validSignatures)
                        put("invalid_signatures", result.invalidSignatures)
                        put("chain_intact", result.chainIntact)
                        put("is_valid", result.isValid)
                        if (result.firstBrokenLink != null) {
                            put("first_broken_link", result.firstBrokenLink)
                        }
                        putJsonArray("issues") {
                            result.issues.forEach { add(it) }
                        }
                    })
                }

                get("/authority/{id}/transactions") {
                    val idStr = call.parameters["id"] ?: return@get call.respondText(
                        "Missing authority ID", status = HttpStatusCode.BadRequest
                    )
                    val authorityId = try {
                        Id.decode(idStr)
                    } catch (e: Exception) {
                        return@get call.respondText(
                            "Invalid authority ID: ${e.message}", status = HttpStatusCode.BadRequest
                        )
                    }

                    val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100

                    val transactions = storage.entityStore.getSignedTransactions(
                        setOf(authorityId),
                        null,
                        TransactionOrder.IdAscending,
                        Int.MAX_VALUE
                    ).toList()

                    val page = transactions.drop(offset).take(limit)

                    call.respond(buildJsonObject {
                        put("total", transactions.size)
                        put("offset", offset)
                        put("limit", limit)
                        putJsonArray("transactions") {
                            for ((index, st) in page.withIndex()) {
                                val tx = st.transaction
                                add(buildJsonObject {
                                    put("index", offset + index)
                                    put("id", tx.id.toString())
                                    put("authority_id", tx.authorityId.toString())
                                    put("epoch_second", tx.epochSecond)
                                    put("timestamp", java.time.format.DateTimeFormatter.ISO_INSTANT.format(
                                        java.time.Instant.ofEpochSecond(tx.epochSecond).atOffset(java.time.ZoneOffset.UTC)
                                    ))
                                    put("entity_count", tx.transactionEntities.size)
                                    put("fact_count", tx.transactionEntities.sumOf { it.facts.size })
                                    putJsonArray("entities") {
                                        for (entity in tx.transactionEntities) {
                                            add(buildJsonObject {
                                                put("entity_id", entity.entityId.toString())
                                                putJsonArray("facts") {
                                                    for (fact in entity.facts) {
                                                        add(buildJsonObject {
                                                            put("attribute", fact.attribute.name)
                                                            put("operation", fact.operation.name)
                                                            put("value_summary", summarizeValue(fact.value))
                                                        })
                                                    }
                                                }
                                            })
                                        }
                                    }
                                })
                            }
                        }
                    })
                }

                get("/authority/{id}/entities") {
                    val idStr = call.parameters["id"] ?: return@get call.respondText(
                        "Missing authority ID", status = HttpStatusCode.BadRequest
                    )
                    val authorityId = try {
                        Id.decode(idStr)
                    } catch (e: Exception) {
                        return@get call.respondText(
                            "Invalid authority ID: ${e.message}", status = HttpStatusCode.BadRequest
                        )
                    }

                    // Collect unique entity IDs from all transactions
                    val transactions = storage.entityStore.getSignedTransactions(
                        setOf(authorityId),
                        null,
                        TransactionOrder.IdAscending,
                        Int.MAX_VALUE
                    ).toList()

                    val entityIds = transactions.flatMap { st ->
                        st.transaction.transactionEntities.map { it.entityId }
                    }.toSet()

                    val entities = storage.entityStore.getEntities(setOf(authorityId), entityIds)

                    call.respond(buildJsonObject {
                        put("total", entities.size)
                        putJsonArray("entities") {
                            for (entity in entities) {
                                add(entityToJson(entity))
                            }
                        }
                    })
                }

                post("/authority/{id}/export") {
                    val idStr = call.parameters["id"] ?: return@post call.respondText(
                        "Missing authority ID", status = HttpStatusCode.BadRequest
                    )
                    val authorityId = try {
                        Id.decode(idStr)
                    } catch (e: Exception) {
                        return@post call.respondText(
                            "Invalid authority ID: ${e.message}", status = HttpStatusCode.BadRequest
                        )
                    }

                    val body = try {
                        call.receive<JsonObject>()
                    } catch (e: Exception) {
                        buildJsonObject {}
                    }

                    val outputPath = body["output_path"]?.jsonPrimitive?.contentOrNull
                        ?.let { Paths.get(it) }
                        ?: defaultOutputDir.resolve(findAuthorityName(authorityId).replace(" ", "-"))

                    val selectedIds = body["transaction_ids"]?.jsonArray
                        ?.map { Id.decode(it.jsonPrimitive.content) }
                        ?.toSet()

                    val name = findAuthorityName(authorityId)
                    val result = exporter.export(authorityId, name, outputPath, selectedIds)

                    call.respond(buildJsonObject {
                        put("output_path", result.outputPath)
                        put("transaction_count", result.transactionCount)
                        put("data_file_count", result.dataFileCount)
                        putJsonArray("errors") {
                            result.errors.forEach { add(it) }
                        }
                    })
                }

                get("/orphaned-transactions") {
                    val result = orphanAnalyzer.analyze()

                    call.respond(buildJsonObject {
                        put("total_filesystem_transactions", result.totalFilesystemTransactions)
                        put("total_db_transactions", result.totalDbTransactions)
                        put("orphan_count", result.orphanCount)
                        putJsonArray("orphans") {
                            for (orphan in result.orphans) {
                                add(orphanToJson(orphan))
                            }
                        }
                    })
                }

                post("/repair-orphans") {
                    val result = orphanAnalyzer.repair()

                    call.respond(buildJsonObject {
                        put("orphans_before", result.orphansBefore)
                        put("orphans_after", result.orphansAfter)
                        put("repaired", result.repaired)
                        putJsonArray("errors") {
                            result.errors.forEach { add(it) }
                        }
                    })
                }

                post("/export-all-orphans") {
                    val body = try {
                        call.receive<JsonObject>()
                    } catch (e: Exception) {
                        buildJsonObject {}
                    }

                    val outputBase = body["output_path"]?.jsonPrimitive?.contentOrNull
                        ?.let { Paths.get(it) }
                        ?: defaultOutputDir

                    val byAuthority = orphanAnalyzer.getAllOrphanTransactionsByAuthority()

                    if (byAuthority.isEmpty()) {
                        call.respond(buildJsonObject {
                            putJsonArray("results") {}
                            put("total_exported", 0)
                        })
                        return@post
                    }

                    val results = mutableListOf<JsonObject>()
                    var totalExported = 0

                    for ((authorityId, transactions) in byAuthority) {
                        val name = findAuthorityName(authorityId)
                        val outputPath = outputBase.resolve(name.replace(" ", "-"))
                        try {
                            val result = exporter.exportTransactions(
                                authorityId, name, transactions, outputPath,
                                sourceLabel = "opencola-kotlin-orphans",
                                filenameTag = "orphaned",
                            )
                            totalExported += result.transactionCount
                            results.add(buildJsonObject {
                                put("authority_id", authorityId.toString())
                                put("authority_name", name)
                                put("output_path", result.outputPath)
                                put("transaction_count", result.transactionCount)
                                put("data_file_count", result.dataFileCount)
                                put("success", true)
                                putJsonArray("errors") { result.errors.forEach { add(it) } }
                            })
                        } catch (e: Exception) {
                            results.add(buildJsonObject {
                                put("authority_id", authorityId.toString())
                                put("authority_name", name)
                                put("success", false)
                                put("error", e.message ?: "Unknown error")
                            })
                        }
                    }

                    call.respond(buildJsonObject {
                        putJsonArray("results") { results.forEach { add(it) } }
                        put("total_exported", totalExported)
                    })
                }

                post("/authority/{id}/export-orphans") {
                    val idStr = call.parameters["id"] ?: return@post call.respondText(
                        "Missing authority ID", status = HttpStatusCode.BadRequest
                    )
                    val authorityId = try {
                        Id.decode(idStr)
                    } catch (e: Exception) {
                        return@post call.respondText(
                            "Invalid authority ID: ${e.message}", status = HttpStatusCode.BadRequest
                        )
                    }

                    val body = try {
                        call.receive<JsonObject>()
                    } catch (e: Exception) {
                        buildJsonObject {}
                    }

                    val outputPath = body["output_path"]?.jsonPrimitive?.contentOrNull
                        ?.let { Paths.get(it) }
                        ?: defaultOutputDir.resolve(findAuthorityName(authorityId).replace(" ", "-"))

                    val name = findAuthorityName(authorityId)
                    val transactions = orphanAnalyzer.getOrphanTransactions(authorityId)

                    if (transactions.isEmpty()) {
                        call.respond(buildJsonObject {
                            put("output_path", "")
                            put("transaction_count", 0)
                            put("data_file_count", 0)
                            putJsonArray("errors") { add("No orphaned transactions found for this authority") }
                        })
                        return@post
                    }

                    val result = exporter.exportTransactions(
                        authorityId, name, transactions, outputPath,
                        sourceLabel = "opencola-kotlin-orphans",
                        filenameTag = "orphaned",
                    )

                    call.respond(buildJsonObject {
                        put("output_path", result.outputPath)
                        put("transaction_count", result.transactionCount)
                        put("data_file_count", result.dataFileCount)
                        putJsonArray("errors") {
                            result.errors.forEach { add(it) }
                        }
                    })
                }
            }
        }
    }

    private fun entityToJson(entity: Entity): JsonObject {
        return buildJsonObject {
            put("entity_id", entity.entityId.toString())
            put("authority_id", entity.authorityId.toString())
            val name = entity.name
            if (name != null) put("name", name)
            val type = entity.type
            if (type != null) put("type", type)
            val desc = entity.description
            if (desc != null) put("description", desc)
            val imageUri = entity.imageUri
            if (imageUri != null) put("image_uri", imageUri.toString())
            // uri is on ResourceEntity and Authority subclasses
            when (entity) {
                is ResourceEntity -> entity.uri?.let { put("uri", it.toString()) }
                is Authority -> entity.uri?.let { put("uri", it.toString()) }
            }
        }
    }

    private fun entryToJson(entry: io.opencola.storage.addressbook.AddressBookEntry, isPersona: Boolean): JsonObject {
        return buildJsonObject {
            put("persona_id", entry.personaId.toString())
            put("entity_id", entry.entityId.toString())
            put("name", entry.name)
            put("is_active", entry.isActive)
            put("is_persona", isPersona)
            put("address", entry.address.toString())
            val imageUri = entry.imageUri
            if (imageUri != null) {
                put("image_uri", imageUri.toString())
            }
            val keyBytes = entry.publicKey.encoded
            val fingerprint = keyBytes.takeLast(8).toByteArray()
            put("public_key_fingerprint", fingerprint.toHexString())
        }
    }

    private fun orphanToJson(orphan: OrphanInfo): JsonObject {
        return buildJsonObject {
            put("id", orphan.id)
            if (orphan.authorityId != null) put("authority_id", orphan.authorityId)
            if (orphan.authorityName != null) put("authority_name", orphan.authorityName)
            if (orphan.timestamp != null) put("timestamp", orphan.timestamp)
            if (orphan.epochSecond != null) put("epoch_second", orphan.epochSecond)
            if (orphan.entityCount != null) put("entity_count", orphan.entityCount)
            if (orphan.factCount != null) put("fact_count", orphan.factCount)
            if (orphan.decodeError != null) put("decode_error", orphan.decodeError)
            putJsonArray("entities") {
                for (entity in orphan.entities) {
                    add(buildJsonObject {
                        put("entity_id", entity.entityId)
                        putJsonArray("facts") {
                            for (fact in entity.facts) {
                                add(buildJsonObject {
                                    put("attribute", fact.attribute)
                                    put("operation", fact.operation)
                                    put("value_summary", fact.valueSummary)
                                })
                            }
                        }
                    })
                }
            }
        }
    }

    private fun findAuthorityName(authorityId: Id): String {
        val entries = storage.addressBook.getEntries()
        return entries.firstOrNull { it.entityId == authorityId }?.name ?: authorityId.toString()
    }

    private fun summarizeValue(value: io.opencola.model.value.Value<Any>): String {
        val raw = value.get()
        return when (raw) {
            is String -> if (raw.length > 80) raw.substring(0, 80) + "..." else raw
            is Id -> raw.toString()
            is java.net.URI -> raw.toString()
            is ByteArray -> "[${raw.size} bytes]"
            is java.security.PublicKey -> "[PublicKey]"
            else -> raw.toString()
        }
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
