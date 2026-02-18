/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.client.v2

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.opencola.relay.common.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.connection.WebSocketSession
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
        return WebSocketSession(
            client.webSocketSession(
                method = HttpMethod.Get,
                host = hostname,
                port = port,
                path = "/v2/relay"
            )
        )
    }
}