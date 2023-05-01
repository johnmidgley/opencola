package io.opencola.model

import java.net.URI

// doc.pdf
// id - hash of data
// source - uri source
// parent - id of parent (container or website)
// name, desc, tags, trust, like, rating
open class DataEntity : Entity {
    var mimeType by stringAttributeDelegate

    constructor(
        authorityId: Id,
        dataId: Id,
        mimeType: String,
        name: String? = null,
        description: String? = null,
        text: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: List<String>? = null,
        ) : super(authorityId, dataId, name, description, text, imageUri, trust, like, rating, tags){
        this.mimeType = mimeType
    }

    constructor(facts: List<Fact>) : super(facts)
}