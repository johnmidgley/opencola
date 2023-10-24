package io.opencola

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.http.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2
import io.opencola.relay.server.plugins.configureRouting
import java.net.URI

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        val address = URI("ocr://0.0.0.0:$defaultOCRPort")
        application {
            install(WebSockets)
            configureRouting(WebSocketRelayServerV1(address), WebSocketRelayServerV2(address))
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assert(response.bodyAsText().startsWith("OpenCola Relay Server"))
    }
}