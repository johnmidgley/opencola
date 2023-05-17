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
            URI("oc://attributes/type"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    MimeType(
        Attribute(
            URI("oc://attributes/mimeType"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Uri(
        Attribute(
            URI("oc://attributes/uri"),
            AttributeType.SingleValue,
            UriValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    DataIds(
        Attribute(
            "dataIds",
            URI("oc://attributes/dataId"),
            AttributeType.MultiValueSet,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            null
        )
    ),
    PublicKey(
        Attribute(
            URI("oc://attributes/publicKey"),
            AttributeType.SingleValue,
            PublicKeyValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Name(
        Attribute(
            URI("oc://attributes/name"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Description(
        Attribute(
            URI("oc://attributes/description"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),

    // TODO: Should text be here, or just retrievable / parseable from the datastore?
    Text(
        Attribute(
            URI("oc://attributes/text"),
            AttributeType.SingleValue,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    ImageUri(
        Attribute(
            URI("oc://attributes/imageUri"),
            AttributeType.SingleValue,
            UriValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Tags(
        Attribute(
            "tags",
            URI("oc://attributes/tag"),
            AttributeType.MultiValueSet,
            StringValue as ValueWrapper<Any>,
            isIndexable = true,
            null)
    ),
    Trust(
        Attribute(
            URI("oc://attributes/trust"),
            AttributeType.SingleValue,
            FloatValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Like(
        Attribute(
            URI("oc://attributes/like"),
            AttributeType.SingleValue,
            BooleanValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Rating(
        Attribute(
            URI("oc://attributes/rating"),
            AttributeType.SingleValue,
            FloatValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    ParentId(
      Attribute(
          URI("oc://attributes/parentId"),
          AttributeType.SingleValue,
          IdValue as ValueWrapper<Any>,
          isIndexable = false,
      )
    ),
    CommentIds(
        Attribute(
            "commentIds",
            URI("oc://attributes/commentId"),
            AttributeType.MultiValueSet,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            computeEntityCommentIds
        )
    ),
    NetworkToken(
        Attribute(
            URI("oc://attributes/networkToken"),
            AttributeType.SingleValue,
            ByteArrayValue as ValueWrapper<Any>,
            isIndexable = false,
        )
    ),
    AttachmentIds(
        Attribute(
            "attachmentIds",
            URI("oc://attributes/attachmentId"),
            AttributeType.MultiValueSet,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            null)
    ),
}





