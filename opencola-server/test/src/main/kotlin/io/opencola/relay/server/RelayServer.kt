package io.opencola.relay.server

import io.ktor.server.netty.*
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.connection.MemoryConnectionDirectory
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.policy.MemoryPolicyStore
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.generateKeyPair
import kotlinx.coroutines.runBlocking
import java.net.URI
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2

class RelayServer(
    val address: URI = URI("ocr://0.0.0.0:$defaultOCRPort"),
    policyStore: PolicyStore = MemoryPolicyStore(),
    connectionDirectory: ConnectionDirectory = MemoryConnectionDirectory(address),
    messageStore: MessageStore = MemoryMessageStore(policyStore)
) {
    companion object {
        // This makes sure all RelayServer instances use the same keypair
        val keyPair = generateKeyPair()
        val config = Config(SecurityConfig(keyPair))
    }

    private val webSocketRelayServerV1 = WebSocketRelayServerV1(config, address)
    private val webSocketRelayServerV2 =
        WebSocketRelayServerV2(config, policyStore, connectionDirectory, messageStore)
    private var nettyApplicationEngine: NettyApplicationEngine? = null

    fun start() {
        nettyApplicationEngine = startWebServer(webSocketRelayServerV1, webSocketRelayServerV2)
    }

    fun stop() {
        runBlocking {
            webSocketRelayServerV1.close()
            webSocketRelayServerV2.close()
        }
        nettyApplicationEngine?.stop(1000, 1000)
    }
}