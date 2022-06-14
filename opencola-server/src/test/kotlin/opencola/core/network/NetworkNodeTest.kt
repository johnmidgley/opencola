package opencola.core.network

import opencola.core.TestApplication
import opencola.core.config.Application
import opencola.core.model.Authority
import opencola.core.security.Encryptor
import opencola.core.storage.AddressBook
import org.junit.Test
import org.kodein.di.instance
import java.io.File
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.test.assertFalse

class NetworkNodeTest {
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

    // Get or create an application instance that will live across test runs. This avoids hammering ZeroTier when
    // just testing communication between nodes.
    private fun getPersistentApplication(num: Int): Application {
        val storagePath = TestApplication.applicationPath.resolve("../test/storage/persistent/application-$num")

        if (!storagePath.exists()) {
            File(storagePath.toString()).mkdirs()
            val configPath = TestApplication.applicationPath.resolve("../test/storage").resolve("opencola-test.yaml")
            configPath.copyTo(storagePath.resolve("opencola-server.yaml"))
        }

        return Application.instance(TestApplication.applicationPath, storagePath).also { setNetworkToken(it) }
    }

    fun connectApplications(app: Application, otherApp: Application) {
        val appAuthority = app.injector.instance<Authority>()
        val appNode by app.injector.instance<NetworkNode>()

        val otherAppAuthority = otherApp.injector.instance<Authority>()
        val otherAppNode by otherApp.injector.instance<NetworkNode>()



    }


    private fun startApplicationNode(num: Int): Application {
        val application = getPersistentApplication(num)
        val networkNode by application.injector.instance<NetworkNode>()
        networkNode.start()
        return application
    }

    // @Test
    fun testLibZTSockets(){
        val app0 = startApplicationNode(0)
        val app1 = startApplicationNode(1)
    }
}