package opencola.core.security

import io.opencola.security.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails


class AesTest {
    init {
        initProvider()
    }

    @Test
    fun testEncryptDecrypt() {
        val secretKey = generateAesKey()
        val input = "Hello World".toByteArray()
        val encryptedBytes = encrypt(secretKey, input)
        val plainText = decrypt(secretKey, encryptedBytes)
        assertContentEquals(input, plainText)
    }

    @Test
    fun testEncryptDecryptBadDecryptIv() {
        assertFails {
            val secretKey = generateAesKey()
            val input = "Hello World".toByteArray()
            val encryptedBytes = encrypt(secretKey, input)
            decrypt(secretKey, encryptedBytes.bytes, generateIv())
        }
    }

}