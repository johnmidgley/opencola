package io.opencola.model

import java.net.URI

// TODO: Allow this to be extended with custom attributes
object Attributes {
    private val attributesByName = CoreAttribute.values().associateBy { it.spec.name }

    fun getAttributeByName(name: String): Attribute? {
        return attributesByName[name]?.spec
    }

    private val attributesByUri = CoreAttribute.values().associateBy { it.spec.uri }

    fun getAttributeByUri(uri: URI): Attribute? {
        return attributesByUri[uri]?.spec
    }

    private val attributeToOrdinal =
        CoreAttribute.values().mapIndexed { index, coreAttribute -> coreAttribute.spec to index }.toMap()

    fun getAttributeOrdinal(attribute: Attribute): Int? {
        return attributeToOrdinal[attribute]
    }
}
