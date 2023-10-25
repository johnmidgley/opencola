package io.opencola.relay.server.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.opencola.relay.common.connection.ConnectionEntry
import io.opencola.relay.common.connection.WebSocketSessionWrapper
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2

fun elapsedTime(startTime: Long): Long {
    return System.currentTimeMillis() - startTime
}

fun connectionsString(connections: Sequence<ConnectionEntry>): String {
    val states = connections.joinToString("\n") { "${it.connection!!.id} - ${it.connection!!.state}" }
    return "Connections (${connections.count()})\n\n$states"
}

fun usageString(usages: Sequence<Usage>): String {
    val usage = usages.joinToString("\n") { "${it.receiver} - ${it.bytesStored}" }
    return "Usage (${usages.count()})\n\n$usage"
}

fun Application.configureRouting(webSocketRelayServerV1: WebSocketRelayServerV1, webSocketRelayServerV2: WebSocketRelayServerV2) {
    routing {
        get("/") {
            call.respondText("OpenCola Relay Server (v1,v2)")
        }

        get("/connections") {
            call.respondText(connectionsString(webSocketRelayServerV1.localConnections()))
        }

        get("/v2/connections") {
            call.respondText(connectionsString(webSocketRelayServerV2.localConnections()))
        }

        get("/v2/usage") {
            call.respondText(usageString(webSocketRelayServerV2.getUsage()))
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