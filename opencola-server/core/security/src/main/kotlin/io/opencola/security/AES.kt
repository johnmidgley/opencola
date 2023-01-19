package io.opencola.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

private val secureRandom = SecureRandom()

data class EncryptionParams(val algorithm: String, val key: SecretKey, val iv: IvParameterSpec)

fun generateAesKey(keySize: Int = 256): SecretKey {
    return KeyGenerator
        .getInstance("AES")
        .also { it.init(keySize) }
        .generateKey()
}

fun generateIv(size: Int = 16): IvParameterSpec{
    return  IvParameterSpec(ByteArray(size).also { secureRandom.nextBytes(it) })
}

fun encrypt(encryptionParams: EncryptionParams, input: ByteArray): ByteArray {
    return Cipher
        .getInstance(encryptionParams.algorithm)
        .also { it.init(Cipher.ENCRYPT_MODE, encryptionParams.key, encryptionParams.iv) }
        .doFinal(input)
}

fun decrypt(encryptionParams: EncryptionParams, cipherText: ByteArray): ByteArray {
    return Cipher
        .getInstance(encryptionParams.algorithm)
        .also { it.init(Cipher.DECRYPT_MODE, encryptionParams.key, encryptionParams.iv) }
        .doFinal(cipherText)
}