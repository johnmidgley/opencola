package io.opencola.relay.server

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opencola.relay.server.plugins.configureRouting
import io.ktor.websocket.*

fun startWebServer(port: Int, wait: Boolean = false): NettyApplicationEngine {
    return embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(WebSockets) {
            // TODO: Check these values
            // pingPeriod = null
            // timeout = Duration.ofHours(1)
            maxFrameSize = 1024 *1024 * 50
            masking = false

        }
        configureRouting(WebSocketRelayServer())
    }.start(wait)
}

fun main() {
    startWebServer(8080, true)
}