package opencola.core.model

import java.net.URI

data class AttributeSpec(val name: String, val uri: URI, val encoder: ByteArrayCodec, val isIndexable: Boolean){
    constructor(uri: URI, encoder: ByteArrayCodec, index: Boolean) : this(uri.path.split("/").last(), uri, encoder, index)
}

// NOTE: In order to be properly searchable, attributes should be added to the search schema.
// TODO: Remove enum - not needed. Just have specs that you can look up by name or URI
// TODO: Create dynamic attribute list for extensibility
// TODO: Comment attribute?
// TODO: DataId attribute
enum class Attributes(val spec: AttributeSpec) {
    Type(AttributeSpec(URI("opencola://attributes/type"), StringAttributeDelegate, false)),
    Uri(AttributeSpec(URI("opencola://attributes/uri"), UriAttributeDelegate,false)),
    DataId(AttributeSpec(URI("opencola://attributes/dataId"), IdAttributeDelegate,false)),
    ImageUri(AttributeSpec(URI("opencola://attributes/imageUri"), UriAttributeDelegate,false)),
    PublicKey(AttributeSpec(URI("opencola://attributes/publicKey"), PublicKeyAttributeDelegate ,false)),
    Name(AttributeSpec(URI("opencola://attributes/name"), StringAttributeDelegate ,true)),
    Description(AttributeSpec(URI("opencola://attributes/description"), StringAttributeDelegate , true)),
    Tags(AttributeSpec(URI("opencola://attributes/tags"), SetOfStringAttributeDelegate,true)),
    Trust(AttributeSpec(URI("opencola://attributes/trust"), FloatAttributeDelegate,true)),
    Like(AttributeSpec( URI("opencola://attributes/like"), BooleanAttributeDelegate,true)),
    Rating(AttributeSpec(URI("opencola://attributes/rating"), FloatAttributeDelegate,true)),
}

private val attributesByName = Attributes.values().associateBy { it.spec.name }

fun getAttributeByName(name: String) : Attributes? {
    return attributesByName[name]
}




