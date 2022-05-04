package opencola.core.model

import opencola.core.serialization.*
import java.net.URI

// NOTE: In order to be properly searchable, attributes should be added to the search schema.
// TODO: Create dynamic attribute list for extensibility
@Suppress("UNCHECKED_CAST")
enum class CoreAttribute(val spec: Attribute) {
    Type(
        Attribute(
            URI("opencola://attributes/type"),
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = false
        )
    ),
    MimeType(
        Attribute(
            URI("opencola://attributes/mimeType"),
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = false
        )
    ),
    Uri(
        Attribute(
            URI("opencola://attributes/uri"), UriByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = false
        )
    ),
    DataId(
        Attribute(
            URI("opencola://attributes/dataId"), Id.Factory as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = false
        )
    ),
    PublicKey(
        Attribute(
            URI("opencola://attributes/publicKey"),
            PublicKeyByteArrayCodec as ByteArrayCodec<Any>,
            false,
            false
        )
    ),
    Name(
        Attribute(
            URI("opencola://attributes/name"), StringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),
    Description(
        Attribute(
            URI("opencola://attributes/description"),
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),

    // TODO: Should text be here, or just retrievable / parseable from the datastore?
    Text(
        Attribute(
            URI("opencola://attributes/text"),
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),
    ImageUri(
        Attribute(
            URI("opencola://attributes/imageUri"),
            UriByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = false
        )
    ),
    Tags(
        Attribute(
            URI("opencola://attributes/tags"),
            SetOfStringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),
    Trust(
        Attribute(
            URI("opencola://attributes/trust"),
            FloatByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),
    Like(
        Attribute(
            URI("opencola://attributes/like"),
            BooleanByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),
    Rating(
        Attribute(
            URI("opencola://attributes/rating"),
            FloatByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = false,
            isIndexable = true
        )
    ),
    Comment(
        Attribute(
            URI("opencola://attributes/comment"),
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isMultiValued = true,
            isIndexable = false
        )
    )
}

private val attributesByName = CoreAttribute.values().associateBy { it.spec.name }

fun getAttributeByName(name: String): Attribute? {
    return attributesByName[name]?.spec
}




