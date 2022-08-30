package io.opencola.core.security

import io.opencola.core.model.Id

class Encryptor(private val keystore: KeyStore) {
    fun encrypt(authorityId: Id, bytes: ByteArray) : ByteArray {
        val publicKey = keystore.getPublicKey(authorityId)
            ?: throw RuntimeException("Unable to find public key for Authority: $authorityId")

        return encrypt(publicKey, bytes)
    }

    fun decrypt(authorityId: Id, bytes: ByteArray) : ByteArray {
        val privateKey = keystore.getPrivateKey(authorityId)
            ?: throw RuntimeException("Unable to find private key for Authority: $authorityId")

        return decrypt(privateKey, bytes)
    }
}