import opencola.core.security.*
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.toHexString
import kotlin.test.Test
import kotlin.test.assertEquals

class ECCTest {
    @Test
    fun testPublicKeyEncoding(){
        val kp = generateKeyPair()

        val public = kp.public
        val public1 = publicKeyFromBytes(public.encoded.toHexString().hexStringToByteArray())
        assertEquals(public, public1)

        val private = kp.private
        val private1 = privateKeyFromBytes(private.encoded.toHexString().hexStringToByteArray())
        assertEquals(private, private1)


        val data = "Data to sign".toByteArray()
        val signature = sign(private, data)
        val signature1 = sign(private1, data)

        // Signatures don't match. even if same key used. Must include timestamp or something random?
        // assertEquals(signature, signature1, "Signatures don't match")

        assert(isValidSignature(public, data, signature))
        assert(isValidSignature(public1, data, signature))
        assert(isValidSignature(public, data, signature1))
        assert(isValidSignature(public1, data, signature1))
    }
}