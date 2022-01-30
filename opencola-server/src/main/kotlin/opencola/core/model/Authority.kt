package opencola.core.model

import java.net.URI
import java.security.KeyPair
import java.security.PrivateKey

class Authority(keyPair: KeyPair, uri: URI? = null, imageUri: URI? = null, name: String? = null, description: String? = null,
                trust: Float? = null, tags: Set<String>? = null, like: Boolean? = null, rating: Float? = null)
    : ActorEntity(Id.ofPublicKey(keyPair.public), keyPair.public, uri, imageUri, name, description, trust, tags, like, rating) {
    // TODO: Private key probably shouldn't be here
    private val privateKey : PrivateKey

    init {
        privateKey = keyPair.private
    }

    // TODO: Access the private key only when signing. Does not need to be stored in the entity.
    fun signTransaction(transaction: Transaction): SignedTransaction {
        return transaction.sign(privateKey)
    }
}