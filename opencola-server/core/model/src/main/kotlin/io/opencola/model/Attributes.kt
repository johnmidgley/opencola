package io.opencola.model

import java.net.URI

import io.opencola.model.protobuf.Model as Proto

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

    // Can be removed after db migration to v2
    private fun convertLegacyUriString(uriString: String): String {
        if (uriString == "opencola://attributes/tags")
            return "oc://attributes/tag"

        return if (uriString.startsWith("opencola:")) uriString.replace("opencola:", "oc:") else uriString
    }

    // Useful in tight loops where there is no URI representation (e.g. a DB)
    fun getAttributeByUriString(uriString: String): Attribute? {
        return attributesByUriString[convertLegacyUriString(uriString)]?.spec
    }

    private val attributeToOrdinal =
        CoreAttribute.values().mapIndexed { index, coreAttribute -> coreAttribute.spec to index }.toMap()

    fun getAttributeOrdinal(attribute: Attribute): Int? {
        return attributeToOrdinal[attribute]
    }

    private val attributeToProtoCoreAttribute = CoreAttribute.values().map { it.spec to it.spec.protoAttribute }.toMap()

    fun getProtoCoreAttribute(attribute: Attribute): Proto.Attribute.CoreAttribute? {
        return attributeToProtoCoreAttribute[attribute]
    }

    private val attributesByProtoCoreAttribute = CoreAttribute.values().map { it.spec.protoAttribute to it }.toMap()

    fun getAttributeByProtoCoreAttribute(protoCoreAttribute: Proto.Attribute.CoreAttribute): Attribute? {
        return attributesByProtoCoreAttribute[protoCoreAttribute]?.spec
    }

    fun get() = CoreAttribute.values().map { it.spec }
}
