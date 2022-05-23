package opencola.core.model

import java.net.URI
import java.security.PublicKey

// TODO: Remove? Private keys are in keystore, so really no difference between an Authority and a regular Actor
class Authority(
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
) : ActorEntity(Id.ofPublicKey(publicKey), publicKey, uri, name, description, text, imageUri, trust, like, rating, tags)