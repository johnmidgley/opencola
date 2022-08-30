package io.opencola.core.model

import java.net.URI

open class ResourceEntity : Entity {
    var uri by nonResettableUriAttributeDelegate
    var dataId by MultiValueSetOfIdAttributeDelegate

    constructor(authorityId: Id,
                uri: URI,
                name: String? = null,
                description: String? = null,
                text: String? = null,
                imageUri: URI? = null,
                trust: Float? = null,
                tags: Set<String>? = null,
                like: Boolean? = null,
                rating: Float? = null,
    ) : super(authorityId, Id.ofUri(uri), name, description, text, imageUri, trust, like, rating, tags){
        if(!uri.isAbsolute)
            throw IllegalArgumentException("Resource URIs must be absolute")
        this.uri = uri
    }

    constructor(facts: List<Fact>) : super(facts)
}