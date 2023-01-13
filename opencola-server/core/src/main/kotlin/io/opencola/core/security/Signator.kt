package io.opencola.core.security

class Signator(private val keystore: KeyStore) {
    fun signBytes(alias: String, bytes: ByteArray): ByteArray {
        // TODO: Sign bytes or hash of bytes? Check performance diff
        val privateKey = keystore.getPrivateKey(alias)
            ?: throw RuntimeException("Unable to find private key for alias: $alias")

        return sign(privateKey, bytes)
    }
}