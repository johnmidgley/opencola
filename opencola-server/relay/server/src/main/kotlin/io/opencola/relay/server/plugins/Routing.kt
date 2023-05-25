package io.opencola.relay.server.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.opencola.relay.common.connection.WebSocketSessionWrapper
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2

fun elapsedTime(startTime: Long): Long {
    return System.currentTimeMillis() - startTime
}

fun connectionsString(connectionStates: List<Pair<String, Boolean>>): String {
    val states = connectionStates.joinToString("\n") { "${it.first} - ${if (it.second) "Ready" else "Not Ready"}" }
    return "Connections (${connectionStates.count()})\n\n$states"
}

fun Application.configureRouting(webSocketRelayServerV1: WebSocketRelayServerV1, webSocketRelayServerV2: WebSocketRelayServerV2) {
    routing {
        get("/") {
            call.respondText("OpenCola Relay Server (v1,v2)")
        }

        get("/connections") {
            call.respondText(connectionsString(webSocketRelayServerV1.connectionStates()))
        }

        get("/v2/connections") {
            call.respondText(connectionsString(webSocketRelayServerV2.connectionStates()))
        }

        webSocket("/relay") {
            webSocketRelayServerV1.handleSession(WebSocketSessionWrapper(this))
        }

        webSocket("/v2/relay") {
            webSocketRelayServerV2.handleSession(WebSocketSessionWrapper(this))
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
            println("/read: Read ${bytes.size} bytes in ${elapsedTime(startTime)} ms")
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