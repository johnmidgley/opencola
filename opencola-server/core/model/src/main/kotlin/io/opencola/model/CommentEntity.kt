package io.opencola.model

import io.opencola.model.value.IdValue
import io.opencola.model.value.StringValue
import java.net.URI

class CommentEntity : Entity {
    var parentId by nonResettableIdAttributeDelegate
    var topLevelParentId by nonResettableIdAttributeDelegate

    constructor(
        authorityId: Id,
        parentId: Id,
        text: String,
        topLevelParentId: Id? = null,
        name: String? = null,
        description: String? = null,
        imageUri: URI? = null,
        trust: Float? = null,
        like: Boolean? = null,
        rating: Float? = null,
        tags: List<String>? = null,
    ) : super(authorityId, Id.new(), name, description, text, imageUri, trust, like, rating, tags) {
        this.parentId = parentId
        this.topLevelParentId = topLevelParentId
    }

    constructor(facts: List<Fact>) : super(facts)
}

private val commentTypeValue = StringValue(CommentEntity::class.java.simpleName)

val computeEntityCommentIds: (Iterable<Fact>) -> Iterable<Fact> = { facts ->
    val typeAttribute = CoreAttribute.Type.spec
    facts
        .filter { fact ->
            fact.attribute == typeAttribute && fact.value as? StringValue == commentTypeValue
        }
        .map { fact ->
            facts.single { it.authorityId == fact.authorityId && it.entityId == fact.entityId && it.attribute == CoreAttribute.ParentId.spec }
        }
        .map {
            val parentId = CoreAttribute.ParentId.spec.valueWrapper.unwrap(it.value) as Id
            val commentIdValue = IdValue(it.entityId).asAnyValue()
            Fact(it.authorityId, parentId, CoreAttribute.CommentIds.spec, commentIdValue, it.operation)
        }
}