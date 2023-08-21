package opencola.core.security

import io.opencola.model.Authority
import io.opencola.security.*
import org.junit.Test
import java.net.URI
import kotlin.io.path.createTempDirectory
import kotlin.test.assertNotNull
import io.opencola.application.TestApplication
import kotlin.test.assertEquals

class KeyStoreTest {
    @Test
    fun testAddKey(){
        val keyStorePath = createTempDirectory().resolve("keystore.fks")
        val password = "password"

        val keyStore = JavaKeyStore(keyStorePath, password)
        val keyPair = generateKeyPair()
        val authority = Authority(keyPair.public, URI(""), "Test Authority")
        keyStore.addKeyPair(authority.authorityId.toString(), keyPair)

        val keyStore1 = JavaKeyStore(keyStorePath, password)

        val privateKey1 = keyStore1.getKeyPair(authority.entityId.toString())?.private
        val publicKey1 = keyStore1.getPublicKey(authority.entityId.toString())

        assertNotNull(privateKey1)
        assertNotNull(publicKey1)

        val data = "data to sign".toByteArray()
        val signature = sign(keyPair.private, data).signature
        val signature1 = sign(privateKey1, data).signature

        assert(isValidSignature(keyPair.public, data, signature))
        assert(isValidSignature(keyPair.public, data, signature1))
        assert(isValidSignature(publicKey1, data, signature))
        assert(isValidSignature(publicKey1, data, signature1))

        val aliases = keyStore1.getAliases()
        assertEquals(1, aliases.size)
        assertEquals(authority.entityId.toString(), aliases[0])
    }

    @Test
    fun testChangePassword() {
        val keyStorePath = TestApplication.getTmpFilePath("pks")
        val password = "password"
        val newPassword = "newPassword"

        val keyStore = JavaKeyStore(keyStorePath, password)
        val keyPair = generateKeyPair()
        val authority = Authority(keyPair.public, URI(""), "Test Authority")
        keyStore.addKeyPair(authority.authorityId.toString(), keyPair)
        assertNotNull(keyStore.getPublicKey(authority.authorityId.toString()))
        keyStore.changePassword(newPassword)
        val pubKey = keyStore.getPublicKey(authority.authorityId.toString())
        assertNotNull(pubKey)

        val keyStore1 = JavaKeyStore(keyStorePath, newPassword)
        val pubKey1 = keyStore1.getPublicKey(authority.authorityId.toString())
        assertNotNull(pubKey1)
        assertEquals(pubKey, pubKey1)
    }
}