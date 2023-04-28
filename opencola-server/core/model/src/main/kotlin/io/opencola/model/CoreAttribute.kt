package io.opencola.model

import io.opencola.security.PublicKeyByteArrayCodec
import io.opencola.serialization.*
import io.opencola.serialization.codecs.*
import java.net.URI

// NOTE: In order to be properly searchable, attributes should be added to the search schema.
// TODO: Create dynamic attribute list for extensibility
// WARNING: ONLY ADD ATTRIBUTES TO THE END OF THIS ENUMERATION
@Suppress("UNCHECKED_CAST")
enum class CoreAttribute(val spec: Attribute) {
    Type(
        Attribute(
            URI("opencola://attributes/type"),
            AttributeType.SingleValue,
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = false
        )
    ),
    MimeType(
        Attribute(
            URI("opencola://attributes/mimeType"),
            AttributeType.SingleValue,
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = false
        )
    ),
    Uri(
        Attribute(
            URI("opencola://attributes/uri"),
            AttributeType.SingleValue,
            UriByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = false
        )
    ),
    DataId(
        Attribute(
            URI("opencola://attributes/dataId"),
            AttributeType.MultiValueSet,
            Id as ByteArrayCodec<Any>,
            isIndexable = false
        )
    ),
    PublicKey(
        Attribute(
            URI("opencola://attributes/publicKey"),
            AttributeType.SingleValue,
            PublicKeyByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = false
        )
    ),
    Name(
        Attribute(
            URI("opencola://attributes/name"),
            AttributeType.SingleValue,
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),
    Description(
        Attribute(
            URI("opencola://attributes/description"),
            AttributeType.SingleValue,
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),

    // TODO: Should text be here, or just retrievable / parseable from the datastore?
    Text(
        Attribute(
            URI("opencola://attributes/text"),
            AttributeType.SingleValue,
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),
    ImageUri(
        Attribute(
            URI("opencola://attributes/imageUri"),
            AttributeType.SingleValue,
            UriByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = false
        )
    ),
    Tags(
        Attribute(
            URI("opencola://attributes/tags"),
            AttributeType.MultiValueSet,
            StringByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),
    Trust(
        Attribute(
            URI("opencola://attributes/trust"),
            AttributeType.SingleValue,
            FloatByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),
    Like(
        Attribute(
            URI("opencola://attributes/like"),
            AttributeType.SingleValue,
            BooleanByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),
    Rating(
        Attribute(
            URI("opencola://attributes/rating"),
            AttributeType.SingleValue,
            FloatByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = true
        )
    ),
    ParentId(
      Attribute(
          URI("opencola://attributes/parentId"),
          AttributeType.SingleValue,
          Id as ByteArrayCodec<Any>,
          isIndexable = false,
      )
    ),
    CommentIds(
        Attribute(
            "commentIds",
            URI("opencola://attributes/commentId"),
            AttributeType.MultiValueSet,
            Id as ByteArrayCodec<Any>,
            isIndexable = false,
            computeEntityCommentIds
        )
    ),
    NetworkToken(
        Attribute(
            URI("opencola://attributes/networkToken"),
            AttributeType.SingleValue,
            BytesByteArrayCodec as ByteArrayCodec<Any>,
            isIndexable = false,
        )
    ),
    AttachmentIds(
        Attribute(
            "attachmentIds",
            URI("opencola://attributes/attachmentId"),
            AttributeType.MultiValueSet,
            Id as ByteArrayCodec<Any>,
            isIndexable = false,
            null)
    ),
}





