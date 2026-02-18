/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.model

import io.opencola.model.value.*
import io.opencola.model.protobuf.Model.Attribute.CoreAttribute as ProtoAttribute
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
            ProtoAttribute.TYPE,
            StringValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    MimeType(
        Attribute(
            URI("oc://attributes/mimeType"),
            AttributeType.SingleValue,
            ProtoAttribute.MIME_TYPE,
            StringValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Uri(
        Attribute(
            URI("oc://attributes/uri"),
            AttributeType.SingleValue,
            ProtoAttribute.URI,
            UriValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    DataIds(
        Attribute(
            "dataIds",
            URI("oc://attributes/dataId"),
            AttributeType.MultiValueSet,
            ProtoAttribute.DATA_ID,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            null
        )
    ),
    PublicKey(
        Attribute(
            URI("oc://attributes/publicKey"),
            AttributeType.SingleValue,
            ProtoAttribute.PUBLIC_KEY,
            PublicKeyValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Name(
        Attribute(
            URI("oc://attributes/name"),
            AttributeType.SingleValue,
            ProtoAttribute.NAME,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Description(
        Attribute(
            URI("oc://attributes/description"),
            AttributeType.SingleValue,
            ProtoAttribute.DESCRIPTION,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),

    // TODO: Should text be here, or just retrievable / parseable from the datastore?
    Text(
        Attribute(
            URI("oc://attributes/text"),
            AttributeType.SingleValue,
            ProtoAttribute.TEXT,
            StringValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    ImageUri(
        Attribute(
            URI("oc://attributes/imageUri"),
            AttributeType.SingleValue,
            ProtoAttribute.IMAGE_URI,
            UriValue as ValueWrapper<Any>,
            isIndexable = false
        )
    ),
    Tags(
        Attribute(
            "tags",
            URI("oc://attributes/tag"),
            AttributeType.MultiValueSet,
            ProtoAttribute.TAG,
            StringValue as ValueWrapper<Any>,
            isIndexable = true,
            null
        )
    ),
    Trust(
        Attribute(
            URI("oc://attributes/trust"),
            AttributeType.SingleValue,
            ProtoAttribute.TRUST,
            FloatValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Like(
        Attribute(
            URI("oc://attributes/like"),
            AttributeType.SingleValue,
            ProtoAttribute.LIKE,
            BooleanValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    Rating(
        Attribute(
            URI("oc://attributes/rating"),
            AttributeType.SingleValue,
            ProtoAttribute.RATING,
            FloatValue as ValueWrapper<Any>,
            isIndexable = true
        )
    ),
    ParentId(
        Attribute(
            URI("oc://attributes/parentId"),
            AttributeType.SingleValue,
            ProtoAttribute.PARENT_ID,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
        )
    ),
    CommentIds(
        Attribute(
            "commentIds",
            URI("oc://attributes/commentId"),
            AttributeType.MultiValueSet,
            ProtoAttribute.COMMENT_ID,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            computeEntityCommentIds
        )
    ),
    NetworkToken(
        Attribute(
            URI("oc://attributes/networkToken"),
            AttributeType.SingleValue,
            ProtoAttribute.NETWORK_TOKEN,
            ByteArrayValue as ValueWrapper<Any>,
            isIndexable = false,
        )
    ),
    AttachmentIds(
        Attribute(
            "attachmentIds",
            URI("oc://attributes/attachmentId"),
            AttributeType.MultiValueSet,
            ProtoAttribute.ATTACHMENT_ID,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
            null
        )
    ),
    TopLevelParentId(
        Attribute(
            URI("oc://attributes/topLevelParentId"),
            AttributeType.SingleValue,
            ProtoAttribute.TOP_LEVEL_PARENT_ID,
            IdValue as ValueWrapper<Any>,
            isIndexable = false,
        )
    ),
    // How far an item is from its origin. Set to set null (which should treated as 0) when an item is added directly
    // (i.e. the item is saved through the browser extension or created as a post). When an item is saved (bubbled),
    // if the origin is another persona, set to 0 (it was saved by the same person), otherwise 1 + min originDistance
    // across all instances.
    OriginDistance(
        Attribute(
            URI("oc://attributes/originDistance"),
            AttributeType.SingleValue,
            ProtoAttribute.ORIGIN_DISTANCE,
            IntValue as ValueWrapper<Any>,
            isIndexable = false,
        )
    ),
}





