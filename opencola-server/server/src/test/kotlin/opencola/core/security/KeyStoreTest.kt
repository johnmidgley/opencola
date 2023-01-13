package opencola.core.security

import io.opencola.core.model.Authority
import io.opencola.core.security.*
import org.junit.Test
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.assertNotNull
import opencola.core.TestApplication
import kotlin.test.assertEquals

class KeyStoreTest {
    @Test
    fun testAddKey(){
        val keyStorePath = createTempDirectory().resolve("keystore.fks")
        val password = "password"

        val keyStore = KeyStore(keyStorePath, password)
        val keyPair = generateKeyPair()
        val authority = Authority(keyPair.public, URI(""), "Test Authority")
        keyStore.addKey(authority.authorityId.toString(), keyPair)

        val keyStore1 = KeyStore(keyStorePath, password)
        val privateKey1 = keyStore1.getPrivateKey(authority.entityId.toString())
        val publicKey1 = keyStore1.getPublicKey(authority.entityId.toString())

        assertNotNull(privateKey1)
        assertNotNull(publicKey1)

        val data = "data to sign".toByteArray()
        val signature = sign(keyPair.private, data)
        val signature1 = sign(privateKey1, data)

        assert(isValidSignature(keyPair.public, data, signature))
        assert(isValidSignature(keyPair.public, data, signature1))
        assert(isValidSignature(publicKey1, data, signature))
        assert(isValidSignature(publicKey1, data, signature1))
    }

    @Test
    fun testChangePassword() {
        val keyStorePath = TestApplication.getTmpFilePath("pks")
        val password = "password"
        val newPassword = "newPassword"

        val keyStore = KeyStore(keyStorePath, password)
        val keyPair = generateKeyPair()
        val authority = Authority(keyPair.public, URI(""), "Test Authority")
        keyStore.addKey(authority.authorityId.toString(), keyPair)
        assertNotNull(keyStore.getPublicKey(authority.authorityId.toString()))
        keyStore.changePassword(newPassword)
        val pubKey = keyStore.getPublicKey(authority.authorityId.toString())
        assertNotNull(pubKey)

        val keyStore1 = KeyStore(keyStorePath, newPassword)
        val pubKey1 = keyStore1.getPublicKey(authority.authorityId.toString())
        assertNotNull(pubKey1)
        assertEquals(pubKey, pubKey1)
    }
}