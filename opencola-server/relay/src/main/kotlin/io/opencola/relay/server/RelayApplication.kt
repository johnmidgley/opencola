package io.opencola.relay.server

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opencola.relay.server.plugins.configureRouting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.ktor.websocket.*

fun startWebServer(port: Int): NettyApplicationEngine {
    return embeddedServer(Netty, port = port, host = "0.0.0.0") {
        install(WebSockets) {
            // TODO: Check these values
            // pingPeriod = Duration.ofSeconds(15)
            // timeout = Duration.ofSeconds(15)
            maxFrameSize = 1024 *1024 * 50
            masking = false
        }
        configureRouting(WebSocketRelayServer())
    }.start()
}

fun main() {
    startWebServer(8080)

    runBlocking {
        launch(Dispatchers.Default) { StandardSocketRelayServer(5796).open() }
    }
}