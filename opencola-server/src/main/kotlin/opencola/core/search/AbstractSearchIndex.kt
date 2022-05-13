package opencola.core.search

import opencola.core.extensions.nullOrElse
import opencola.core.model.Attribute
import opencola.core.model.AttributeType.*
import opencola.core.model.Entity

abstract class AbstractSearchIndex : SearchIndex {
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