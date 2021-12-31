package opencola.core.model

import java.net.URI

// NOTE: In order to be properly searchable, attributes should be added to the search schema.
// TODO: Create dynamic attribute list for extensibility
@Suppress("UNCHECKED_CAST")
enum class CoreAttribute(val spec: Attribute) {
    Type(Attribute(URI("opencola://attributes/type"), StringByteArrayCodec as ByteArrayCodec<Any>, false)),
    Uri(Attribute(URI("opencola://attributes/uri"), UriByteArrayCodec as ByteArrayCodec<Any> ,false)),
    DataId(Attribute(URI("opencola://attributes/dataId"), Id.Factory as ByteArrayCodec<Any>,false)),
    ImageUri(Attribute(URI("opencola://attributes/imageUri"), UriByteArrayCodec as ByteArrayCodec<Any>,false)),
    PublicKey(Attribute(URI("opencola://attributes/publicKey"), PublicKeyByteArrayCodec as ByteArrayCodec<Any> ,false)),
    Name(Attribute(URI("opencola://attributes/name"), StringByteArrayCodec as ByteArrayCodec<Any>,true)),
    Description(Attribute(URI("opencola://attributes/description"), StringByteArrayCodec as ByteArrayCodec<Any>, true)),
    Tags(Attribute(URI("opencola://attributes/tags"), SetOfStringByteArrayCodec as ByteArrayCodec<Any>,true)),
    Trust(Attribute(URI("opencola://attributes/trust"), FloatByteArrayCodec as ByteArrayCodec<Any>,true)),
    Like(Attribute( URI("opencola://attributes/like"), BooleanByteArrayCodec as ByteArrayCodec<Any>,true)),
    Rating(Attribute(URI("opencola://attributes/rating"), FloatByteArrayCodec as ByteArrayCodec<Any>,true))
}

private val attributesByName = CoreAttribute.values().associateBy { it.spec.name }

fun getAttributeByName(name: String) : Attribute? {
    return attributesByName[name]?.spec
}




