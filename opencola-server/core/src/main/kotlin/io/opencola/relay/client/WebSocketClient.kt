package io.opencola.relay.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.websocket.*
import io.ktor.http.*
import io.opencola.relay.common.*
import java.security.KeyPair

class WebSocketClient(
    hostname: String,
    port: Int,
    keyPair: KeyPair,
    name: String? = null,
    requestTimeoutMilliseconds: Long = 10000,
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : AbstractClient(hostname, port, keyPair, name, requestTimeoutMilliseconds, retryPolicy) {
        private val client = HttpClient(CIO) {
        install(WebSockets) {
            // Configure WebSockets
        }
    }

    override suspend fun getSocketSession(): SocketSession {
        return WebSocketSessionWrapper(client.webSocketSession(method = HttpMethod.Get, host = hostname, port = port, path = "/relay"))
    }
}