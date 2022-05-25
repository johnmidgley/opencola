package opencola.core.security

import opencola.core.model.Authority
import org.junit.Test
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.assertNotNull

class KeyStoreTest {
    @Test
    fun testAddKey(){
        val keyStorePath = createTempDirectory().resolve("keystore.fks")
        val password = "password"

        val keyStore = KeyStore(keyStorePath, password)
        val keyPair = generateKeyPair()
        val authority = Authority(keyPair.public, URI(""), "Test Authority")
        keyStore.addKey(authority.authorityId, keyPair)

        val keyStore1 = KeyStore(keyStorePath, password)
        val privateKey1 = keyStore1.getPrivateKey(authority.entityId)
        val publicKey1 = keyStore1.getPublicKey(authority.entityId)

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
}