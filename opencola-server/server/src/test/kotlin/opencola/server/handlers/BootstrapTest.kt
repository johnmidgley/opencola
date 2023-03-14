package opencola.server.handlers

import io.opencola.application.Application
import io.opencola.application.TestApplication
import org.junit.Test
import kotlin.test.assertEquals

class BootstrapTest {
    @Test
    fun testChangeAuthorityStorePassword() {
        val password = "password"
        val newPassword = "newPassword"
        val storagePath = TestApplication.getTmpDirectory(".storage")

        val keyPair = Application.getOrCreateRootKeyPair(storagePath, password).single()
        changeAuthorityKeyStorePassword(storagePath, password, newPassword)

        val keyPair1 = Application.getOrCreateRootKeyPair(storagePath, newPassword).single()
        assertEquals(keyPair.public, keyPair1.public)
    }
}