package opencola.core.model

import java.net.URI
import java.security.PublicKey

// TODO: Remove? Private keys are in keystore, so really no difference between an Authority and a regular Actor
class Authority : Entity {
    var uri by uriAttributeDelegate
    var publicKey by publicKeyAttributeDelegate
    var networkToken by byteArrayAttributeDelegate

    constructor(
        authorityId: Id,
        publicKey: PublicKey, // TODO: Id should not depend on public key, since public key may change
        uri: URI,
        name: String,
        description: String? = null,
        text: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: Set<String>? = null,
    ) : super(authorityId, Id.ofPublicKey(publicKey), name, description, text, imageUri, trust, like, rating, tags) {
        this.uri = uri
        this.publicKey = publicKey
    }

    constructor(
        publicKey: PublicKey,
        uri: URI,
        name: String,
        description: String? = null,
        text: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: Set<String>? = null,
    ) : this(Id.ofPublicKey(publicKey), publicKey, uri, name, description, text, imageUri, trust, like, rating, tags)

    constructor(facts: List<Fact>) : super(facts)
}