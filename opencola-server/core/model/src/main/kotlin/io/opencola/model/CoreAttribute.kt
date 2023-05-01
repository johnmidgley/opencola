package io.opencola.model

import io.opencola.model.value.*
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
            StringValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    MimeType(
        Attribute(
            URI("opencola://attributes/mimeType"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Uri(
        Attribute(
            URI("opencola://attributes/uri"),
            AttributeType.SingleValue,
            UriValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    DataId(
        Attribute(
            URI("opencola://attributes/dataId"),
            AttributeType.MultiValueSet,
            IdValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    PublicKey(
        Attribute(
            URI("opencola://attributes/publicKey"),
            AttributeType.SingleValue,
            PublicKeyValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Name(
        Attribute(
            URI("opencola://attributes/name"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Description(
        Attribute(
            URI("opencola://attributes/description"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),

    // TODO: Should text be here, or just retrievable / parseable from the datastore?
    Text(
        Attribute(
            URI("opencola://attributes/text"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    ImageUri(
        Attribute(
            URI("opencola://attributes/imageUri"),
            AttributeType.SingleValue,
            UriValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Tags(
        Attribute(
            URI("opencola://attributes/tags"),
            AttributeType.MultiValueSet,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Trust(
        Attribute(
            URI("opencola://attributes/trust"),
            AttributeType.SingleValue,
            FloatValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Like(
        Attribute(
            URI("opencola://attributes/like"),
            AttributeType.SingleValue,
            BooleanValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Rating(
        Attribute(
            URI("opencola://attributes/rating"),
            AttributeType.SingleValue,
            FloatValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    ParentId(
      Attribute(
          URI("opencola://attributes/parentId"),
          AttributeType.SingleValue,
          IdValue as ValueWrapper<Any>,
          isIndexable = false,
      )
    ),
    CommentIds(
        Attribute(
            "commentIds",
            URI("opencola://attributes/commentId"),
            AttributeType.MultiValueSet,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            computeEntityCommentIds
        )
    ),
    NetworkToken(
        Attribute(
            URI("opencola://attributes/networkToken"),
            AttributeType.SingleValue,
            ByteArrayValue as ValueWrapper<Any>,
            isIndexable = false,
        )
    ),
    AttachmentIds(
        Attribute(
            "attachmentIds",
            URI("opencola://attributes/attachmentId"),
            AttributeType.MultiValueSet,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            null)
    ),
}





