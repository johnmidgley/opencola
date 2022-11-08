package io.opencola.relay.server.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.opencola.relay.common.WebSocketSessionWrapper
import io.opencola.relay.server.WebSocketRelayServer

fun elapsedTime(startTime: Long): Long {
    return System.currentTimeMillis() - startTime
}

fun Application.configureRouting(webSocketRelayServer: WebSocketRelayServer) {
    routing {
        get("/") {
            call.respondText("OpenCola Relay Server")
        }

        get("/connections") {
            val connectionStates = webSocketRelayServer.connectionStates()
            val states = connectionStates.joinToString("\n") { "${it.first} - ${if (it.second) "Ready" else "Not Ready"}"  }
            call.respondText("OpenCola Relay Server\n\nConnections (${connectionStates.count()})\n\n$states")
        }

        webSocket("/relay") {
            webSocketRelayServer.handleSession(WebSocketSessionWrapper(this))
        }

        get("/send") {
            val size = call.parameters["size"]?.toInt() ?: 0
            val buffer = ".".repeat(size).toByteArray()
            val startTime = System.currentTimeMillis()
            call.respondBytes(buffer)
            println("/send: Sent $size bytes in ${elapsedTime(startTime)} ms")
        }

        post("/read") {
            val startTime = System.currentTimeMillis()
            val bytes = call.receiveText().toByteArray()
            println ("/read: Read ${bytes.size} bytes in ${elapsedTime(startTime)} ms")
            call.respondText { "OK" }
        }

        post("/echo") {
            val readStart = System.currentTimeMillis()
            val bytes = call.receiveText().toByteArray()
            println("/echo: Read ${bytes.size} bytes in ${elapsedTime(readStart)} ms")
            val writeStart = System.currentTimeMillis()
            call.respondBytes(bytes)
            println("/echo: Sent ${bytes.size} bytes in ${elapsedTime(writeStart)} ms")
        }
    }
}