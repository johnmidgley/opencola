/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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
        .flatMap { fact ->
            listOf(
                facts.single { it.authorityId == fact.authorityId && it.entityId == fact.entityId && it.attribute == CoreAttribute.ParentId.spec },
                facts.singleOrNull { it.authorityId == fact.authorityId && it.entityId == fact.entityId && it.attribute == CoreAttribute.TopLevelParentId.spec }
            )
        }
        .mapNotNull { it }
        .map {
            val parentId = it.attribute.valueWrapper.unwrap(it.value) as Id
            val commentIdValue = IdValue(it.entityId).asAnyValue()
            Fact(it.authorityId, parentId, CoreAttribute.CommentIds.spec, commentIdValue, it.operation)
        }
}