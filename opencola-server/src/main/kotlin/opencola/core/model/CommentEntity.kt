package opencola.core.model

import opencola.core.extensions.nullOrElse

class CommentEntity : Entity {
    var parentId by nonResettableIdAttributeDelegate
    var text by stringAttributeDelegate
    var like by booleanAttributeDelegate
    var rating by floatAttributeDelegate

    constructor(
        authorityId: Id,
        parentId: Id,
        text: String,
        like: Boolean? = null,
        rating: Float? = null,
    ) : super(authorityId, Id.new()) {
        this.parentId = parentId
        this.text = text
        like.nullOrElse { this.like = it }
        rating.nullOrElse { this.rating = it }
    }

    constructor(facts: List<Fact>) : super(facts)
}

private val commentTypeValue = Value(CoreAttribute.Type.spec.codec.encode(CommentEntity::class.java.simpleName))

val computeEntityCommentIds: (facts: Iterable<Fact>) -> Iterable<Fact> = { facts ->
    facts
        .filter { it.attribute == CoreAttribute.Type.spec && it.value == commentTypeValue }
        .map { fact ->
            facts.single { it.authorityId == fact.authorityId && it.entityId == fact.entityId && it.attribute == CoreAttribute.ParentId.spec }
        }
        .map {
            println(it)
            it
        }
        .map {
            val parentId = CoreAttribute.ParentId.spec.codec.decode(it.value.bytes) as Id
            val commentIdValue = Value(CoreAttribute.CommentIds.spec.codec.encode(it.entityId))
            Fact(
                it.authorityId,
                parentId,
                CoreAttribute.CommentIds.spec,
                commentIdValue,
                it.operation,
            )
        }
}