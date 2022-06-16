package opencola.core.network

import opencola.core.TestApplication
import opencola.core.config.Application
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.runCommand
import opencola.core.extensions.startProcess
import opencola.core.io.MultiStreamReader
import opencola.core.model.Authority
import opencola.core.network.zerotier.ZeroTierClient
import opencola.core.security.Encryptor
import opencola.core.storage.AddressBook
import opencola.server.PeerTest
import org.junit.Test
import org.kodein.di.instance
import java.io.File
import java.lang.Thread.sleep
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull


class NetworkNodeTest : PeerTest() {
    private val ztAuthToken = "THx5SAwGhzwiWSXUWfDjv073qF8u3mz0"
    // @Test
    fun testInvalidToken(){
        val networkNode by TestApplication.instance.injector.instance<NetworkNode>()
        assertFalse(networkNode.isNetworkTokenValid(""))
    }

    // TODO: Make method of address book?
    private fun setNetworkToken(application: Application) {
        val injector = application.injector
        val appAuthority by injector.instance<Authority>()
        val addressBook by injector.instance<AddressBook>()
        val encryptor by injector.instance<Encryptor>()
        val authority = addressBook.getAuthority(appAuthority.entityId) ?: throw RuntimeException("Missing root authority")
        authority.networkToken = encryptor.encrypt(appAuthority.entityId, ztAuthToken.toByteArray())
        addressBook.updateAuthority(authority)
    }

    private fun setRootAuthorityName(instance: Application, name: String){
        val rootAuthority by instance.injector.instance<Authority>()
        val addressBook by instance.injector.instance<AddressBook>()
        val authority = addressBook.getAuthority(rootAuthority.authorityId)!!
        authority.name = name
        addressBook.updateAuthority(authority)
    }

    private fun getApplication(storagePath: Path, name: String): Application {
        if (!storagePath.exists()) {
            File(storagePath.toString()).mkdirs()
            val configPath = TestApplication.applicationPath.resolve("../test/storage").resolve("opencola-test.yaml")
            configPath.copyTo(storagePath.resolve("opencola-server.yaml"))
        }

        val instance = Application.instance(TestApplication.applicationPath, storagePath).also { setNetworkToken(it) }
        setRootAuthorityName(instance, "Application $name")

        return Application.instance(TestApplication.applicationPath, storagePath).also { setNetworkToken(it) }
    }

    // Get or create an application instance that will live across test runs. This avoids hammering ZeroTier when
    // just testing communication between nodes.
    private fun getPersistentApplication(num: Int): Application {
        val storagePath = TestApplication.applicationPath.resolve("../test/storage/persistent/application-$num")
        return getApplication(storagePath, num.toString())
    }

    private fun getApplication(num: Int): Application {
        val storagePath = TestApplication.storagePath.resolve("application-$num")
        return getApplication(storagePath, num.toString())
    }

    private fun startApplicationNode(num: Int): Application {
        val application = getPersistentApplication(num)
        val networkNode by application.injector.instance<NetworkNode>()
        networkNode.start()
        return application
    }

    private fun deleteAllNetworks(){
        val zeroTierClient = ZeroTierClient(ztAuthToken)
        zeroTierClient.getNetworks().forEach{
            zeroTierClient.deleteNetwork(it.id!!)
        }
    }

    private fun assertAuthoritiesAreSame(authority0: Authority, authority1: Authority){
        assertEquals(authority0.entityId, authority1.entityId)
        assertEquals(authority0.name, authority1.name)
        assertEquals(authority0.uri, authority1.uri)
        assertEquals(authority0.publicKey, authority1.publicKey)
        assertEquals(authority0.imageUri, authority1.imageUri)
    }

    // @Test
    fun testLibZTSockets() {
        deleteAllNetworks()

        val app0 = getPersistentApplication(0)
        val rootAuthority0 by app0.injector.instance<Authority>()
        val networkNode0 by app0.injector.instance<NetworkNode>()
        val addressBook0 by app0.injector.instance<AddressBook>()

        networkNode0.start()
        val authority0 = addressBook0.getAuthority(rootAuthority0.entityId)!!
        assertEquals(authority0, addressBook0.getAuthorities().single())

        val app1 = getPersistentApplication(1)
        val rootAuthority1 by app1.injector.instance<Authority>()
        val networkNode1 by app1.injector.instance<NetworkNode>()
        val addressBook1 by app1.injector.instance<AddressBook>()

        networkNode1.start()
        val authority1 = addressBook1.getAuthority(rootAuthority1.entityId)!!
        assertEquals(authority1, addressBook1.getAuthorities().single())

        val inviteToken0 = networkNode0.getInviteToken()
        networkNode1.addPeer(inviteToken0)

        val app1peer0 = addressBook1.getAuthority(authority0.entityId)
        assertNotNull(app1peer0)
        assertAuthoritiesAreSame(authority0, app1peer0)

        val inviteToken1 = networkNode1.getInviteToken()
        networkNode0.addPeer(inviteToken1)

        val app0peer1 = addressBook0.getAuthority(authority1.entityId)
        assertNotNull(app0peer1)
        assertAuthoritiesAreSame(authority1, app0peer1)
    }

    private val workingDir = File("../test")

    private fun makeNodes() {
        "./make-nodes".runCommand(workingDir)
    }

    private fun startNode(num: Int): Process? {
        return "./start-node node$num".startProcess(workingDir)
    }

    fun stopNodes(){
        val ps = "ps -eaf"
            .runCommand()
            .filter { it.contains("../../install/opencola/server") }
            .map { line -> line.split(" ").filter { it.isNotBlank() }[1] }
            .forEach{
                "kill $it".runCommand()
            }
    }

    // @Test
    fun testZtLibPeers() {
        val process = startNode(0)!!

        MultiStreamReader(listOf(Pair("STD", process.inputStream), Pair("ERR",process.errorStream))).use { reader ->
            while(process.isAlive)
                reader.readLine().nullOrElse { println(it) } ?: sleep(50)
        }

        process.destroy()
    }
}