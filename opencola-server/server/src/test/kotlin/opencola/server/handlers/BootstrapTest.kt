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
    @Test
    fun testChangeAuthorityStorePassword() {
        val password = "password"
        val newPassword = "newPassword"
        val storagePath = TestApplication.getTmpDirectory(".storage")

        val keyPair = Application.getOrCreateRootKeyPair(storagePath, password)
        changeAuthorityKeyStorePassword(storagePath, password, newPassword)

        val keyPair1 = Application.getOrCreateRootKeyPair(storagePath, newPassword)
        assertEquals(keyPair.public, keyPair1.public)
    }
}