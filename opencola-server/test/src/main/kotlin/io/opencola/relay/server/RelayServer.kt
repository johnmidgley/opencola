package io.opencola.relay.server

import io.ktor.server.netty.*
import io.opencola.application.TestApplication
import io.opencola.event.log.EventLogger
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.connection.ExposedConnectionDirectory
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.policy.ExposedPolicyStore
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.generateKeyPair
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.newContentAddressedFileStore
import io.opencola.storage.newSQLiteDB
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.net.URI
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2

class RelayServer(
    val address: URI = URI("ocr://0.0.0.0:$defaultOCRPort"),
    baseConfig: Config? = null,
    val db: Database = newSQLiteDB("RelayServer"),
    val contentAddressedFileStore: ContentAddressedFileStore = newContentAddressedFileStore("MessageStore"),
    val policyStore: PolicyStore = ExposedPolicyStore(db, securityConfig.rootId),
    val connectionDirectory: ConnectionDirectory = ExposedConnectionDirectory(db, address),
    val messageStore: MessageStore = ExposedMessageStore(
        db,
        baseConfig?.capacity?.maxBytesStored ?: CapacityConfig().maxBytesStored,
        contentAddressedFileStore,
        policyStore
    )
) {
    companion object {
        // This makes sure all RelayServer instances use the same keypair
        val keyPair = generateKeyPair()
        val rootKeyPair = generateKeyPair()
        val securityConfig = SecurityConfig(keyPair, rootKeyPair)

        fun getConfig(config: Config): Config {
            return Config(
                config.capacity,
                SecurityConfig(keyPair, rootKeyPair)
            )

        }
    }

    private val config = getConfig(baseConfig ?: Config(CapacityConfig(), securityConfig))
    private val eventLogger = EventLogger("relay", TestApplication.getTmpDirectory("events"))
    private val webSocketRelayServerV1 = WebSocketRelayServerV1(config, eventLogger, address)
    private val webSocketRelayServerV2 =
        WebSocketRelayServerV2(config, eventLogger, policyStore, connectionDirectory, messageStore)
    private var nettyApplicationEngine: NettyApplicationEngine? = null

    fun start() {
        nettyApplicationEngine = startWebServer(
            config.capacity,
            eventLogger,
            webSocketRelayServerV1,
            webSocketRelayServerV2
        )
    }

    fun stop() {
        runBlocking {
            webSocketRelayServerV1.close()
            webSocketRelayServerV2.close()
        }
        nettyApplicationEngine?.stop(1000, 1000)
    }
}