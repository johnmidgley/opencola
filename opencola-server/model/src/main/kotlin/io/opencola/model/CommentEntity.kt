package io.opencola.model

import java.net.URI

class CommentEntity : Entity {
    var parentId by nonResettableIdAttributeDelegate

    constructor(
        authorityId: Id,
        parentId: Id,
        text: String,
        name: String? = null,
        description: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: Set<String>? = null,
    ) : super(authorityId, Id.new(), name, description, text, imageUri, trust, like, rating, tags) {
        this.parentId = parentId
    }

    constructor(facts: List<Fact>) : super(facts)
}

private val commentTypeValue = Value(CoreAttribute.Type.spec.codec.encode(CommentEntity::class.java.simpleName))

val computeEntityCommentIds: (Iterable<Fact>) -> Iterable<Fact> = { facts ->
    facts
        .filter { it.attribute == CoreAttribute.Type.spec && it.value == commentTypeValue }
        .map { fact ->
            facts.single { it.authorityId == fact.authorityId && it.entityId == fact.entityId && it.attribute == CoreAttribute.ParentId.spec }
        }
        .map {
            val parentId = CoreAttribute.ParentId.spec.codec.decode(it.value.bytes) as Id
            val commentIdValue = Value(CoreAttribute.CommentIds.spec.codec.encode(it.entityId))
            Fact(it.authorityId, parentId, CoreAttribute.CommentIds.spec, commentIdValue, it.operation)
        }
}