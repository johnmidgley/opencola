package opencola.core.model

import java.net.URI

// NOTE: In order to be properly searchable, attributes should be added to the search schema.
// TODO: Remove enum - not needed. Just have specs that you can look up by name or URI
// TODO: Create dynamic attribute list for extensibility
enum class CoreAttribute(val spec: Attribute) {
    Type(Attribute(URI("opencola://attributes/type"), StringAttributeDelegate, false)),
    Uri(Attribute(URI("opencola://attributes/uri"), UriAttributeDelegate,false)),
    DataId(Attribute(URI("opencola://attributes/dataId"), IdAttributeDelegate,false)),
    ImageUri(Attribute(URI("opencola://attributes/imageUri"), UriAttributeDelegate,false)),
    PublicKey(Attribute(URI("opencola://attributes/publicKey"), PublicKeyAttributeDelegate ,false)),
    Name(Attribute(URI("opencola://attributes/name"), StringAttributeDelegate ,true)),
    Description(Attribute(URI("opencola://attributes/description"), StringAttributeDelegate , true)),
    Tags(Attribute(URI("opencola://attributes/tags"), SetOfStringAttributeDelegate,true)),
    Trust(Attribute(URI("opencola://attributes/trust"), FloatAttributeDelegate,true)),
    Like(Attribute( URI("opencola://attributes/like"), BooleanAttributeDelegate,true)),
    Rating(Attribute(URI("opencola://attributes/rating"), FloatAttributeDelegate,true))
}

private val attributesByName = CoreAttribute.values().associateBy { it.spec.name }

fun getAttributeByName(name: String) : Attribute? {
    return attributesByName[name]?.spec
}




