package io.opencola.security

import java.security.PublicKey

class Encryptor(private val keystore: KeyStore) {
    private fun getPublicKey(alias: String): PublicKey {
        return keystore.getPublicKey(alias)
            ?: throw RuntimeException("Unable to find public key for alias: $alias")
    }

    fun encrypt(alias: String, bytes: ByteArray, transformation: EncryptionTransformation = DEFAULT_ENCRYPTION_TRANSFORMATION): EncryptedBytes {
        return encrypt(getPublicKey(alias), bytes, transformation)
    }

    fun decrypt(alias: String, encryptedBytes: EncryptedBytes): ByteArray {
        val privateKey = keystore.getKeyPair(alias)?.private
            ?: throw RuntimeException("Unable to find private key for alias: $alias")

        return decrypt(privateKey, encryptedBytes)
    }
}