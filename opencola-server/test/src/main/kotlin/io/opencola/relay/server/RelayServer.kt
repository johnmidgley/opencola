package io.opencola.relay.server

import io.ktor.server.netty.*
import io.opencola.application.TestApplication
import io.opencola.event.log.EventLogger
import io.opencola.model.Id
import io.opencola.relay.common.connection.ConnectionDirectory
import io.opencola.relay.common.connection.ExposedConnectionDirectory
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.policy.ExposedPolicyStore
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.generateKeyPair
import io.opencola.storage.db.getSQLiteDB
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createDirectory
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2

class RelayServer(
    val address: URI = URI("ocr://0.0.0.0:$defaultOCRPort"),
    val storagePath: Path = TestApplication.getTmpDirectory("relay-storage"),
    baseConfig: Config? = null,
    val db: Database = getSQLiteDB(storagePath.resolve("relay.db")),
    val contentAddressedFileStore: ContentAddressedFileStore = FileSystemContentAddressedFileStore(storagePath.resolve("messages")),
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
        val securityConfig = SecurityConfig(keyPair, Id.ofPublicKey(rootKeyPair.public))

        fun getConfig(config: Config): Config {
            return Config(config.storagePath, config.server, config.capacity, securityConfig)
        }
    }

    private val config = getConfig(baseConfig ?: Config(storagePath, security = securityConfig))
    private val eventLogger = EventLogger("relay", storagePath.resolve("events").createDirectory())
    private val webSocketRelayServerV1 = WebSocketRelayServerV1(config, eventLogger, address)
    private val webSocketRelayServerV2 =
        WebSocketRelayServerV2(config, eventLogger, policyStore, connectionDirectory, messageStore)
    private var nettyApplicationEngine: NettyApplicationEngine? = null

    fun start() {
        nettyApplicationEngine = startWebServer(
            config,
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