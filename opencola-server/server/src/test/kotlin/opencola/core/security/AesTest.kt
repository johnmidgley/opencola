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
        val encryptionParams = EncryptionParams("AES/CBC/PKCS5Padding", generateAesKey(), generateIv())
        val input = "Hello World".toByteArray()
        val cipherText = encrypt(encryptionParams, input)
        val plainText = decrypt(encryptionParams, cipherText)
        assertContentEquals(input, plainText)
    }

    @Test
    fun testEncryptDecryptBadDecryptIv() {
        assertFails {
            val encryptionParams = EncryptionParams("AES/CBC/PKCS5Padding", generateAesKey(), generateIv())
            val input = "Hello World".toByteArray()
            val cipherText = encrypt(encryptionParams, input)
            val badEncryptionParams = EncryptionParams("AES/CBC/PKCS5Padding", encryptionParams.key, generateIv())
            decrypt(badEncryptionParams, cipherText)
        }
    }

}