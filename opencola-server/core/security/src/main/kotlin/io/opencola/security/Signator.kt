package io.opencola.security

// TODO: Get rid of this and pass around address book?
class Signator(private val keystore: KeyStore) {
    fun signBytes(alias: String, bytes: ByteArray): Signature {
        // TODO: Sign bytes or hash of bytes? Check performance diff
        val privateKey = keystore.getKeyPair(alias)?.private
            ?: throw RuntimeException("Unable to find private key for alias: $alias")

        return sign(privateKey, bytes).signature
    }

    fun canSign(alias: String): Boolean {
        return keystore.getKeyPair(alias)?.private != null
    }
}