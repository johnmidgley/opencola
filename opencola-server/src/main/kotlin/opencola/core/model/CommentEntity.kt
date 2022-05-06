package opencola.core.model

import opencola.core.extensions.nullOrElse

class CommentEntity : Entity {
    var text by StringAttributeDelegate
    var like by BooleanAttributeDelegate
    var rating by FloatAttributeDelegate

    constructor(
        authorityId: Id,
        text: String,
        like: Boolean?,
        rating: Float?,
    ) : super(authorityId, Id.new()) {
        this.text = text
        like.nullOrElse { this.like = it }
        rating.nullOrElse { this.rating = it }
    }
}