package opencola.core.model

import java.net.URI

open class ResourceEntity : Entity {
    var uri by uriAttributeDelegate
    var dataId by idAttributeDelegate

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
        this.uri = uri
    }

    constructor(facts: List<Fact>) : super(facts)
}