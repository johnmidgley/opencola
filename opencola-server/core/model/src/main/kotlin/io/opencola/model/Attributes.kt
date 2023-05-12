package io.opencola.model

import java.net.URI

// TODO: Allow this to be extended with custom attributes
// TODO: Turn into AttributeRegistry
object Attributes {
    private val attributesByName = CoreAttribute.values().associateBy { it.spec.name }

    fun getAttributeByName(name: String): Attribute? {
        return attributesByName[name]?.spec
    }

    private val attributesByUri = CoreAttribute.values().associateBy { it.spec.uri }

    fun getAttributeByUri(uri: URI): Attribute? {
        return attributesByUri[uri]?.spec
    }

    private val attributesByUriString = CoreAttribute.values().associateBy { it.spec.uri.toString() }

    // Useful in tight loops where there is no URI representation (e.g. a DB)
    fun getAttributeByUriString(uriString: String): Attribute? {
        return attributesByUriString[uriString]?.spec
    }

    private val attributeToOrdinal =
        CoreAttribute.values().mapIndexed { index, coreAttribute -> coreAttribute.spec to index }.toMap()

    fun getAttributeOrdinal(attribute: Attribute): Int? {
        return attributeToOrdinal[attribute]
    }

    fun get() = CoreAttribute.values().map { it.spec }
}
