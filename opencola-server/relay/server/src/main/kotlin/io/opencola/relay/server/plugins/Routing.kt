/*
 * Copyright 2024 OpenCola
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

package io.opencola.relay.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.opencola.model.Id
import io.opencola.relay.common.connection.ConnectionEntry
import io.opencola.relay.common.connection.WebSocketSession
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.security.publicKeyFromBytes
import io.opencola.util.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val usage = usages.joinToString("\n") { "${it.to} - ${it.numBytes}" }
    return "Usage (${usages.count()})\n\n$usage"
}

fun Application.configureRouting(
    webSocketRelayServerV1: WebSocketRelayServerV1,
    webSocketRelayServerV2: WebSocketRelayServerV2
) {
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

        post("/v2/deliver/{connection-id}") {
            withContext(Dispatchers.IO) {
                val id = call.parameters["connection-id"]!!
                call.respond(HttpStatusCode.Accepted)
                webSocketRelayServerV2.sendStoredMessages(Id.decode(id))
            }
        }

        post("/v2/forward/{fromPublicKey}") {
            withContext(Dispatchers.IO) {
                val from = publicKeyFromBytes(Base58.decode(call.parameters["fromPublicKey"]!!))
                val payload = call.receiveStream().readAllBytes()
                call.respond(HttpStatusCode.Accepted)
                webSocketRelayServerV2.handleForwardedMessage(from, payload)
            }
        }

        webSocket("/relay") {
            webSocketRelayServerV1.handleSession(WebSocketSession(this))
        }

        webSocket("/v2/relay") {
            // TODO: Return proper errors (e.g. 401 when authentication fails)
            webSocketRelayServerV2.handleSession(WebSocketSession(this))
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