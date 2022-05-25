package opencola.core.model

import java.net.URI

class PostEntity : Entity {
    constructor(authorityId: Id,
                entityId: Id,
                name: String? = null,
                description: String? = null,
                text: String? = null,
                imageUri: URI? = null,
                trust: Float? = null,
                tags: Set<String>? = null,
                like: Boolean? = null,
                rating: Float? = null,
    ) : super(authorityId, entityId, name, description, text, imageUri, trust, like, rating, tags)

    constructor(authorityId: Id,
                name: String? = null,
                description: String? = null,
                text: String? = null,
                imageUri: URI? = null,
                trust: Float? = null,
                tags: Set<String>? = null,
                like: Boolean? = null,
                rating: Float? = null
    ) : this(authorityId, Id.new(), name, description, text, imageUri, trust, tags, like, rating)

    constructor(facts: List<Fact>) : super(facts)
}