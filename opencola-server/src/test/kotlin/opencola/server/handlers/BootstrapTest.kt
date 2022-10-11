package opencola.server.handlers

import io.opencola.core.config.Application
import io.opencola.core.config.SSLConfig
import opencola.core.TestApplication
import opencola.server.getSSLCertificateStore
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.test.assertEquals

class BootstrapTest {
    // TODO: Move to more general place
    private fun initStorage(storagePath: Path) {
        val certDirectory = storagePath.resolve("cert")
        certDirectory.createDirectory()
        TestApplication.applicationPath.resolve("../storage/cert/gen-ssl-cert").copyTo(certDirectory.resolve("gen-ssl-cert"))
    }

    @Test
    fun testChangePasswordsJustAuthorityStore() {
        val password = "password"
        val newPassword = "newPassword"
        val storagePath = TestApplication.getTmpDirectory(".storage")

        val keyPair = Application.getOrCreateRootKeyPair(storagePath, password)
        changePasswords(storagePath, password, newPassword)

        val keyPair1 = Application.getOrCreateRootKeyPair(storagePath, newPassword)
        assertEquals(keyPair.public, keyPair1.public)
    }

    @Test
    fun testChangePasswordsJustCertStore() {
        val password = "password"
        val newPassword = "newPassword"
        val storagePath = TestApplication.getTmpDirectory(".storage").also { initStorage(it) }
        val sslConfig = TestApplication.instance.config.server.ssl ?: SSLConfig()

        val keyStore = getSSLCertificateStore(storagePath, password, sslConfig)
        val sslCert = keyStore.getCertificate("opencola-ssl")

        changeSSLCertStorePassword(storagePath, password, newPassword)
        val keyStore1 = getSSLCertificateStore(storagePath, newPassword, sslConfig
        )
        val sslCert1 = keyStore1.getCertificate("opencola-ssl")

        assertEquals(sslCert.publicKey, sslCert1.publicKey)
    }

    @Test
    fun testChangePasswords() {
        val storagePath = TestApplication.getTmpDirectory(".storage").also { initStorage(it) }
        val sslConfig = TestApplication.instance.config.server.ssl ?: SSLConfig()
        val password = "password"
        val newPassword = "newPassword"

        val keyPair = Application.getOrCreateRootKeyPair(storagePath, password)
        val sslCert = getSSLCertificateStore(storagePath, password, sslConfig).getCertificate("opencola-ssl")

        changePasswords(storagePath, password, newPassword)

        val keyPair1 = Application.getOrCreateRootKeyPair(storagePath, newPassword)
        val sslCert1 = getSSLCertificateStore(storagePath, newPassword, sslConfig).getCertificate("opencola-ssl")

        assertEquals(keyPair.public, keyPair1.public)
        assertEquals(sslCert.publicKey, sslCert1.publicKey)
    }
}