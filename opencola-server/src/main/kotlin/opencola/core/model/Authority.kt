package opencola.core.model

import java.net.URI
import java.security.PublicKey

class Authority(publicKey: PublicKey, uri: URI? = null, imageUri: URI? = null, name: String? = null, description: String? = null,
                trust: Float? = null, tags: Set<String>? = null, like: Boolean? = null, rating: Float? = null)
    : ActorEntity(Id.ofPublicKey(publicKey), publicKey, uri, imageUri, name, description, trust, tags, like, rating)