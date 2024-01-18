package io.opencola.search

import io.opencola.model.Attribute
import io.opencola.model.AttributeType.*
import io.opencola.model.Entity
import io.opencola.model.Id
import io.opencola.security.hash.Sha256Hash

abstract class AbstractSearchIndex : SearchIndex {
    protected val logger = mu.KotlinLogging.logger("SearchIndex")

    protected fun getDocId(authorityId: Id, entityId: Id): String {
        // For some reason this doesn't work if base58 is used (testLuceneRepeatIndexing fails). Are there restrictions on doc ids?
        return Sha256Hash.ofString("${authorityId}:${entityId}").toHexString()
    }

    fun getAttributeAsText(entity: Entity, attribute: Attribute): String? {
        return when (attribute.type) {
            SingleValue -> entity.getValue(attribute.name)?.get()?.toString()

            MultiValueSet -> entity.getSetValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { it.get().toString() }

            MultiValueList -> entity.getListValues(attribute.name)
                .ifEmpty { null }
                ?.joinToString { it.get().toString() }
        }
    }
}