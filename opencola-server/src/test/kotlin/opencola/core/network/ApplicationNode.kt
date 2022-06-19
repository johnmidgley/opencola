package opencola.core.network

import io.ktor.server.netty.*
import opencola.core.TestApplication
import opencola.core.config.Application
import opencola.core.model.Authority
import opencola.core.storage.AddressBook
import opencola.server.getServer
import opencola.server.handlers.*
import java.io.File
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists

class ApplicationNode(private val application: Application) : Node {
    var server: NettyApplicationEngine? = null

    override fun make() {
        // Nothing to do
    }

    override fun start(): Node {
        server = getServer(application).also { it.start() }
        return this
    }

    override fun stop() {
        server?.stop(1000, 1000)
    }

    override fun setNetworkToken(token: String) {
        val app = application
        val peer = getPeers(app.inject(), app.inject())
            .let { result -> result.results.single { it.id == result.authorityId } }
        val peer1 = Peer(peer.id, peer.name, peer.publicKey, peer.address, peer.imageUri, peer.isActive, token)
        updatePeer(app.inject(), app.inject(), app.inject(), app.inject(), peer1)
    }

    override fun getInviteToken(): String {
        return getToken(application.inject(), application.inject())
    }

    override fun getPeers(): PeersResult {
        return getPeers(application.inject(), application.inject())
    }


    companion object Factory {
        private fun setRootAuthorityName(instance: Application, name: String){
            val rootAuthority = instance.inject<Authority>()
            val addressBook = instance.inject<AddressBook>()
            val authority = addressBook.getAuthority(rootAuthority.authorityId)!!
            authority.name = name
            addressBook.updateAuthority(authority)
        }

        private fun getNode(storagePath: Path, name: String): Node {
            if (!storagePath.exists()) {
                File(storagePath.toString()).mkdirs()
                val configPath = TestApplication.applicationPath.resolve("../test/storage").resolve("opencola-test.yaml")
                // TODO: Should update port
                configPath.copyTo(storagePath.resolve("opencola-server.yaml"))
            }

            val instance = Application.instance(TestApplication.applicationPath, storagePath)
            setRootAuthorityName(instance, "Application $name")

            return ApplicationNode(instance)
        }

        fun getNode(num: Int, persistent: Boolean = false): Node {
            val storagePath =
                if(persistent)
                    TestApplication.applicationPath.resolve("../test/storage/persistent/application-$num")
                else
                    TestApplication.storagePath.resolve("application-$num")

            return getNode(storagePath, num.toString())
        }
    }
}
