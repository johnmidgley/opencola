package io.opencola.relay.server.plugins

import io.ktor.routing.*
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.websocket.*
import io.opencola.relay.common.WebSocketSessionWrapper
import io.opencola.relay.server.WebSocketRelayServer

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
    }
}
