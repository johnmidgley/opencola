package opencola.core.security

import opencola.core.model.Id

class Signator(private val keystore: KeyStore) {
    fun signBytes(authorityId: Id, bytes: ByteArray): ByteArray {
        val privateKey = keystore.getPrivateKey(authorityId)
            ?: throw RuntimeException("Unable to find private key for Authority: $authorityId")

        return sign(privateKey, bytes)
    }
}