package io.opencola.relay.client.v2

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.opencola.relay.common.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.connection.WebSocketSessionWrapper
import java.net.URI
import java.security.KeyPair

class WebSocketClient(
    uri: URI,
    keyPair: KeyPair,
    name: String? = null,
    connectTimeoutMilliseconds: Long = 3000,
    requestTimeoutMilliseconds: Long = 20000,
    retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : Client(uri, keyPair, name, connectTimeoutMilliseconds, requestTimeoutMilliseconds, retryPolicy) {
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            // Configure WebSockets
            pingInterval = 1000 * 55 // TODO: Make configurable
        }
    }

    override suspend fun getSocketSession(): SocketSession {
        return WebSocketSessionWrapper(
            client.webSocketSession(
                method = HttpMethod.Get,
                host = hostname,
                port = port,
                path = "/v2/relay"
            )
        )
    }
}