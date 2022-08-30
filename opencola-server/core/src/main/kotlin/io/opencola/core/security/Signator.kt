package io.opencola.core.security

import io.opencola.core.model.Id

class Signator(private val keystore: KeyStore) {
    fun signBytes(authorityId: Id, bytes: ByteArray): ByteArray {
        // TODO: Sign bytes or hash of bytes? Check performance diff
        val privateKey = keystore.getPrivateKey(authorityId)
            ?: throw RuntimeException("Unable to find private key for Authority: $authorityId")

        return sign(privateKey, bytes)
    }
}