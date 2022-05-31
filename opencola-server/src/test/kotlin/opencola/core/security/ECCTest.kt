package opencola.core.security

import kotlin.test.Test
import kotlin.test.assertEquals

class ECCTest {
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
}