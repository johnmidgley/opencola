package io.opencola.relay.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.opencola.relay.common.connection.MemoryConnectionDirectory
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.server.plugins.configureRouting
import io.opencola.security.generateKeyPair
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.URI
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

fun startWebServer(
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
            maxFrameSize = 1024 * 1024 * 50 // TODO: Config
            masking = false

        }
        configureRouting(webSocketRelayServerV1, webSocketRelayServerV2)
        this.environment.monitor.subscribe(ApplicationStarted) { startSemaphore.release() }
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

fun main() {
    // TODO: Pass in keypair
    val config = Config(SecurityConfig(generateKeyPair()))
    val address = URI("ocr://0.0.0.0:$defaultOCRPort")
    val serverV1 = WebSocketRelayServerV1(config, address)
    val serverV2 = WebSocketRelayServerV2(
        config,
        MemoryConnectionDirectory(address),
        MemoryMessageStore(),
    )

    startWebServer(serverV1, serverV2, wait = true)
}