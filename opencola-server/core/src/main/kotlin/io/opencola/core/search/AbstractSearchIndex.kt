package io.opencola.core.search

import opencola.core.extensions.nullOrElse
import opencola.core.extensions.toHexString
import io.opencola.core.model.Attribute
import io.opencola.core.model.AttributeType.*
import io.opencola.core.model.Entity
import io.opencola.core.model.Id
import io.opencola.core.security.sha256

abstract class AbstractSearchIndex : SearchIndex {
    protected fun getDocId(authorityId: Id, entityId: Id): String {
        // For some reason this doesn't work if base58 is used (testLuceneRepeatIndexing fails). Are there restrictions on doc ids?
        return sha256("${authorityId}:${entityId}").toHexString()
    }

    fun getAttributeAsText(entity: Entity, attribute: Attribute) : String? {
        return when(attribute.type){
            SingleValue -> entity.getValue(attribute.name)
                .nullOrElse { attribute.codec.decode(it.bytes).toString() }
            MultiValueSet -> entity.getSetValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { attribute.codec.decode(it.bytes).toString() }
            MultiValueList -> entity.getListValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { attribute.codec.decode(it.bytes).toString() }
        }
    }
}