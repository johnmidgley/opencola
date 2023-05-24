package io.opencola

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.http.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.opencola.relay.server.v1.WebSocketRelayServer
import io.opencola.relay.server.plugins.configureRouting

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            install(WebSockets)
            configureRouting(WebSocketRelayServer())
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assert(response.bodyAsText().startsWith("OpenCola Relay Server"))
    }
}