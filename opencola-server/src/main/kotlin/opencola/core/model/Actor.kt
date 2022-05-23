package opencola.core.model

import java.net.URI
import java.security.PublicKey

// Person or website (organization), identified by hash of public key
//  TODO: Figure out how to properly initialize and store publicKey attribute
open class ActorEntity : Entity {
    var uri by uriAttributeDelegate
    var publicKey by publicKeyAttributeDelegate

    constructor(
        authorityId: Id,
        publicKey: PublicKey,
        uri: URI? = null,
        name: String? = null,
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

    constructor(facts: List<Fact>) : super(facts)
}