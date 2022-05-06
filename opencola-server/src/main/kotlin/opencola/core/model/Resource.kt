package opencola.core.model

import java.net.URI

open class ResourceEntity : Entity {
    var uri by UriAttributeDelegate
    var dataId by IdAttributeDelegate
    var name by StringAttributeDelegate
    var description by StringAttributeDelegate
    var text by StringAttributeDelegate
    var imageUri by UriAttributeDelegate
    var trust by FloatAttributeDelegate
    var tags by SetOfStringAttributeDelegate
    var like by BooleanAttributeDelegate
    var rating by FloatAttributeDelegate
    var commentIds by MultiValueSetOfIdAttributeDelegate

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
                commentIds: List<Id>? = null,
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
        if(commentIds != null) this.commentIds = commentIds
    }

    constructor(facts: List<Fact>) : super(facts)
}