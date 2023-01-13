package opencola.core.security

import io.opencola.security.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ECCTest : SecurityProviderDependent() {
    @Test
    fun testPublicKeyEncoding(){
        val kp = generateKeyPair()

        val public = kp.public
        val public1 = decodePublicKey(public.encode())
        assertEquals(public, public1)

        val private = kp.private
        val data = "Data to sign".toByteArray()
        val signature = sign(private, data)

        // Signatures don't match. even if same key used. Must include timestamp or something random?
        // assertEquals(signature, signature1, "Signatures don't match")
        assert(isValidSignature(public, data, signature))
        assert(isValidSignature(public1, data, signature))
    }

    @Test
    fun testEncryptDecrypt() {
        val keyPair = generateKeyPair()
        val data = "This is a test string to be encrypted and then decrypted"

        val encrypted = encrypt(keyPair.public, data.toByteArray())
        val decrypted = String(decrypt(keyPair.private, encrypted))

        assertEquals(data, decrypted)
    }
}