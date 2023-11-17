package io.opencola.relay.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.opencola.event.log.EventLogger
import io.opencola.model.Id
import io.opencola.relay.common.connection.ExposedConnectionDirectory
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.relay.common.policy.ExposedPolicyStore
import io.opencola.storage.db.getSQLiteDB
import io.opencola.relay.server.plugins.configureRouting
import io.opencola.security.generateKeyPair
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.system.exitProcess


private val logger = KotlinLogging.logger("relay")

fun startWebServer(
    capacityConfig: CapacityConfig,
    eventLogger: EventLogger,
    webSocketRelayServerV1: WebSocketRelayServerV1,
    webSocketRelayServerV2: WebSocketRelayServerV2,
    wait: Boolean = false
): NettyApplicationEngine {
    require(webSocketRelayServerV1.address == webSocketRelayServerV2.address) { "WebSocketRelayServer addresses must match" }
    val address = webSocketRelayServerV1.address
    val startSemaphore = Semaphore(1).also { it.acquire() }

    val module: Application.() -> Unit = {
        install(WebSockets) {
            // TODO: Check these values
            // pingPeriod = null
            // timeout = Duration.ofHours(1)
            maxFrameSize = capacityConfig.maxPayloadSize
            masking = false

        }
        configureRouting(webSocketRelayServerV1, webSocketRelayServerV2)
        this.environment.monitor.subscribe(ApplicationStarted) {
            eventLogger.log("RelayStarted", mapOf("address" to address.toString()))
            startSemaphore.release()
        }
    }

    thread {
        runBlocking {
            launch {
                webSocketRelayServerV1.open()
            }
            launch {
                webSocketRelayServerV2.open()
            }
        }
    }

    val server = embeddedServer(Netty, port = address.port, host = address.host, module = module).start(wait)

    startSemaphore.acquire()
    return server
}

fun validateStoragePath(storagePath: String?) : Path {
    if(storagePath == null) {
        logger.error { "No storage path specified" }
        exitProcess(1)
    }

    Path(storagePath).let {
        if (!it.exists()) {
            logger.error { "Storage path does not exist: $storagePath" }
            exitProcess(1)
        } else if (!it.isDirectory()) {
            logger.error { "Storage path is not a directory: $storagePath" }
            exitProcess(1)
        }

        return it
    }
}

fun main(args: Array<String>) {
    // TODO: Pass in keypair
    val config = Config(CapacityConfig(), SecurityConfig(generateKeyPair(), Id.ofPublicKey(generateKeyPair().public)))
    val address = URI("ocr://0.0.0.0:$defaultOCRPort")
    val commandLineArgs = CommandLineArgs(args)

    logger.info { "Starting relay server at $address" }
    logger.info { "Args: ${args.joinToString(" ")}" }
    logger.info { "$config" }

    // TODO: Add dependency injection
    val storagePath = validateStoragePath(commandLineArgs.storage)
    val eventsPath = storagePath.resolve("events").also { Files.createDirectories(it) }
    val eventLogger = EventLogger("relay", eventsPath)
    val relayDB = getSQLiteDB(storagePath.resolve("relay.db"))
    val policyStore = ExposedPolicyStore(relayDB,config.security.rootId)
    val fileStore = FileSystemContentAddressedFileStore(storagePath.resolve("messages"))
    val serverV1 = WebSocketRelayServerV1(config, eventLogger, address)
    val serverV2 = WebSocketRelayServerV2(
        config,
        eventLogger,
        policyStore,
        ExposedConnectionDirectory(relayDB, address),
        ExposedMessageStore(relayDB, config.capacity.maxBytesStored, fileStore, policyStore),
    )

    startWebServer(config.capacity, eventLogger, serverV1, serverV2, wait = true)
}