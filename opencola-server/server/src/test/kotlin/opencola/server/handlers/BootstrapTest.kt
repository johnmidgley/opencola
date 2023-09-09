package opencola.server.handlers

import io.opencola.application.Application
import io.opencola.application.TestApplication
import io.opencola.security.hash.Sha256Hash
import io.opencola.security.keystore.defaultPasswordHash
import org.junit.Test
import kotlin.test.assertEquals

class BootstrapTest {
    @Test
    fun testChangeAuthorityStorePassword() {
        val passwordHash = defaultPasswordHash
        val newPasswordHash = Sha256Hash.ofString("newPassword")
        val storagePath = TestApplication.getTmpDirectory(".storage")

        val keyPair = Application.getOrCreateRootKeyPair(storagePath, passwordHash).single()
        changeAuthorityKeyStorePassword(storagePath, passwordHash, newPasswordHash)

        val keyPair1 = Application.getOrCreateRootKeyPair(storagePath, newPasswordHash).single()
        assertEquals(keyPair.public, keyPair1.public)
    }
}