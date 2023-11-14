package io.opencola

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.http.*
import kotlin.test.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.opencola.relay.common.connection.MemoryConnectionDirectory
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.policy.MemoryPolicyStore
import io.opencola.relay.server.Config
import io.opencola.relay.server.SecurityConfig
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2
import io.opencola.relay.server.plugins.configureRouting
import io.opencola.security.generateKeyPair
import java.net.URI

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        val config = Config(security = SecurityConfig(generateKeyPair(), generateKeyPair()))
        val address = URI("ocr://0.0.0.0:$defaultOCRPort")
        val policyStore = MemoryPolicyStore(config.security.rootId)
        application {
            install(WebSockets)
            configureRouting(
                WebSocketRelayServerV1(config, address),
                WebSocketRelayServerV2(
                    config,
                    policyStore,
                    MemoryConnectionDirectory(address),
                    MemoryMessageStore(1024, policyStore)
                )
            )
        }

        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assert(response.bodyAsText().startsWith("OpenCola Relay Server"))
    }
}