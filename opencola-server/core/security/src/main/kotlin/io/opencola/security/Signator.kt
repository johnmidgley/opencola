package io.opencola.security

class Signator(private val keystore: KeyStore) {
    fun signBytes(alias: String, bytes: ByteArray): Signature {
        // TODO: Sign bytes or hash of bytes? Check performance diff
        val privateKey = keystore.getKeyPair(alias)?.private
            ?: throw RuntimeException("Unable to find private key for alias: $alias")

        return sign(privateKey, bytes)
    }

    fun canSign(alias: String): Boolean {
        return keystore.getKeyPair(alias)?.private != null
    }
}