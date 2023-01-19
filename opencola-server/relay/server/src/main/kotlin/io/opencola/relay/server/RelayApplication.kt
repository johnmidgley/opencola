package io.opencola.relay.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
// TODO: Remove dependency on client - move value to common
import io.opencola.relay.client.defaultOCRPort
import io.opencola.relay.server.plugins.configureRouting
import java.util.concurrent.Semaphore

fun startWebServer(
    port: Int,
    webSocketRelayServer: WebSocketRelayServer = WebSocketRelayServer(),
    wait: Boolean = false): NettyApplicationEngine {
    val startSemaphore = Semaphore(1).also { it.acquire() }

    val server = embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(WebSockets) {
            // TODO: Check these values
            // pingPeriod = null
            // timeout = Duration.ofHours(1)
            maxFrameSize = 1024 * 1024 * 50 // TODO: Config
            masking = false

        }
        configureRouting(webSocketRelayServer)
        this.environment.monitor.subscribe(ApplicationStarted) { startSemaphore.release() }
    }.start(wait)

    startSemaphore.acquire()
    return server
}

fun main() {
    startWebServer(defaultOCRPort, wait =  true)
}