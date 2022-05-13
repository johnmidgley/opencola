package opencola.core.model

import java.net.URI

open class ResourceEntity : Entity {
    var uri by uriAttributeDelegate
    var dataId by idAttributeDelegate
    var name by stringAttributeDelegate
    var description by stringAttributeDelegate
    var text by stringAttributeDelegate
    var imageUri by imageUriAttributeDelegate
    var trust by floatAttributeDelegate
    var tags by tagsAttributeDelegate
    var like by booleanAttributeDelegate
    var rating by floatAttributeDelegate
    val commentIds by MultiValueSetOfIdAttributeDelegate // Read only, computed property

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
    ) : super(authorityId, Id.ofUri(uri)){
        // Null checks are more efficient, but more importantly, don't result in retracting facts
        this.uri = uri
        if(name != null) this.name = name
        if(description != null) this.description = description
        if(text != null) this.text = text
        if(imageUri != null) this.imageUri = imageUri
        if(trust != null) this.trust = trust
        if(tags != null) this.tags = tags
        if(like != null) this.like = like
        if(rating != null) this.rating = rating
    }

    constructor(facts: List<Fact>) : super(facts)
}