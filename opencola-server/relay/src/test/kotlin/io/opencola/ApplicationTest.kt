package io.opencola

import io.ktor.application.*
import io.ktor.http.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import io.opencola.relay.server.WebSocketRelayServer
import io.opencola.relay.server.plugins.configureRouting

class ApplicationTest {
    @Test
    fun testRoot() {
        withTestApplication({
            install(WebSockets) {
                // TODO: Check these values
                // pingPeriod = Duration.ofSeconds(15)
                // timeout = Duration.ofSeconds(15)
                maxFrameSize = 1024 *1024 * 50
                masking = false
            }
            configureRouting(WebSocketRelayServer())
        }) {
            handleRequest(HttpMethod.Get, "/").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                assert(response.content!!.startsWith("OpenCola Relay Server"))
            }
        }
    }
}