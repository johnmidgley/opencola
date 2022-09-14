package io.opencola

import io.ktor.http.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.opencola.relay.server.WebSocketRelayServer
import io.opencola.relay.server.plugins.configureRouting

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({ configureRouting(WebSocketRelayServer()) }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals("OpenCola Relay Server", response.content)
            }
        }
    }
}