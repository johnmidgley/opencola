package opencola.core.network

import io.ktor.server.netty.*
import io.opencola.application.*
import opencola.core.TestApplication
import io.opencola.event.EventBus
import io.opencola.network.NetworkConfig
import io.opencola.network.NetworkNode
import io.opencola.storage.AddressBook
import io.opencola.storage.PersonaAddressBookEntry
import opencola.server.AuthToken
import opencola.server.getServer
import opencola.server.handlers.*
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists

class ApplicationNode(val application: Application) : Node {
    private var server: NettyApplicationEngine? = null

    override fun make() {
        // Nothing to do
    }

    override fun start(): Node {
        server = getServer(application, AuthToken.encryptionParams).also { it.start() }
        return this
    }

    override fun stop() {
        application.inject<NetworkNode>().stop()
        application.inject<EventBus>().stop()
        server?.stop(500, 500)
    }

    override fun getInviteToken(): String {
        return getInviteToken(
            application.getPersonas().single().entityId,
            application.inject(),
            application.inject(),
            application.inject()
        )
    }

    override fun postInviteToken(token: String): Peer {
        return inviteTokenToPeer(application.inject(), token)
    }

    override fun getPeers(): PeersResult {
        return getPeers(application.inject(), application.inject())
    }

    override fun updatePeer(peer: Peer) {
        updatePeer(
            application.getPersonas().single().entityId,
            application.inject(),
            application.inject(),
            application.inject(),
            peer
        )
    }

    companion object Factory {
        // TODO: Move to interface or base class
        private const val basePort = 5750

        private fun setRootAuthorityName(instance: Application, name: String) {
            instance.inject<AddressBook>()
                .let { addressBook ->
                    addressBook
                        .getEntries()
                        .filterIsInstance<PersonaAddressBookEntry>()
                        .single()
                        .also {
                            val entry = PersonaAddressBookEntry(
                                it.personaId,
                                it.entityId,
                                it.name,
                                it.publicKey,
                                it.address,
                                null,
                                it.isActive,
                                it.keyPair
                            )
                            addressBook.updateEntry(entry)
                        }
                }
        }

        // TODO: Move to interface or base class
        fun getBaseConfig(): Config {
            val configPath = TestApplication.applicationPath.resolve("../../test/storage").resolve("opencola-test.yaml")
            return loadConfig(configPath)
        }

        private fun getNode(storagePath: Path, name: String, port: Int, config: Config? = null): ApplicationNode {
            if (!storagePath.exists()) {
                File(storagePath.toString()).mkdirs()
                val configPath =
                    TestApplication.applicationPath.resolve("../../test/storage").resolve("opencola-test.yaml")
                configPath.copyTo(storagePath.resolve("opencola-server.yaml"))
            }

            val configToUse = (config ?: loadConfig(storagePath.resolve("opencola-server.yaml"))).let {
                it
                    .setServer(ServerConfig(it.server.host, port, null))
                    .setNetwork(
                        NetworkConfig(
                            URI("http://${it.server.host}:$port"),
                            it.network.requestTimeoutMilliseconds,
                            it.network.socksProxy
                        )
                    )
            }

            val instance = Application.instance(storagePath, "password", configToUse)
            setRootAuthorityName(instance, name)

            return ApplicationNode(instance)
        }

        fun getNode(num: Int, persistent: Boolean = false, config: Config? = null): ApplicationNode {
            val storagePath =
                if (persistent)
                    TestApplication.applicationPath.resolve("../../test/storage/persistent/node-$num")
                else
                    TestApplication.storagePath.resolve("node-$num")

            return getNode(storagePath, "Node: $num", basePort + num, config)
        }
    }
}
