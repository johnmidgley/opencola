package opencola.core.model

import java.net.URI
import java.security.PublicKey

// Person or website (organization), identified by hash of public key
//  TODO: Figure out how to properly initialize and store publicKey attribute
// Could probably clean this up with delegated properties
open class ActorEntity : Entity {
    var uri by UriAttributeDelegate
    var imageUri by UriAttributeDelegate
    var name by StringAttributeDelegate
    var description by StringAttributeDelegate
    var publicKey by PublicKeyAttributeDelegate
    var trust by FloatAttributeDelegate
    var tags by SetOfStringAttributeDelegate
    var like by BooleanAttributeDelegate
    var rating by FloatAttributeDelegate

    constructor(authorityId: Id,
                publicKey: PublicKey,
                uri: URI? = null,
                imageUri: URI? = null,
                name: String? = null,
                description: String? = null,
                trust: Float? = null,
                tags: Set<String>? = null,
                like: Boolean? = null,
                rating: Float? = null,
    ) : super(authorityId, Id.ofPublicKey(publicKey)){
        // Null checks are more efficient, but more importantly, don't result in retracting facts
        if(uri != null) this.uri = uri
        if(imageUri != null) this.imageUri = imageUri
        if(name != null) this.name = name
        if(description != null) this.description = description
        this.publicKey = publicKey
        if(trust != null) this.trust = trust
        if(tags != null) this.tags = tags
        if(like != null) this.like = like
        if(rating != null) this.rating = rating
    }

    constructor(facts: List<Fact>) : super(facts)
}