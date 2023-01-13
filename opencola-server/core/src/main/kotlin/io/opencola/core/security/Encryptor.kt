package io.opencola.core.security

class Encryptor(private val keystore: KeyStore) {
    fun encrypt(alias: String, bytes: ByteArray) : ByteArray {
        val publicKey = keystore.getPublicKey(alias)
            ?: throw RuntimeException("Unable to find public key for alias: $alias")

        return encrypt(publicKey, bytes)
    }

    fun decrypt(alias: String, bytes: ByteArray) : ByteArray {
        val privateKey = keystore.getPrivateKey(alias)
            ?: throw RuntimeException("Unable to find private key for alias: $alias")

        return decrypt(privateKey, bytes)
    }
}