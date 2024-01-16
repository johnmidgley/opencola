package io.opencola.model

import org.junit.Test
import kotlin.test.assertEquals

class AttributeTest {
    @Test
    fun testCoreAttributeStability() {
        assertEquals(0, CoreAttribute.Type.ordinal)
        assertEquals(CoreAttribute.Type.spec.protoAttribute, CoreAttribute.Type.spec.protoAttribute)

        assertEquals(1, CoreAttribute.MimeType.ordinal)
        assertEquals(CoreAttribute.MimeType.spec.protoAttribute, CoreAttribute.MimeType.spec.protoAttribute)

        assertEquals(2, CoreAttribute.Uri.ordinal)
        assertEquals(CoreAttribute.Uri.spec.protoAttribute, CoreAttribute.Uri.spec.protoAttribute)

        assertEquals(3, CoreAttribute.DataIds.ordinal)
        assertEquals(CoreAttribute.DataIds.spec.protoAttribute, CoreAttribute.DataIds.spec.protoAttribute)

        assertEquals(4, CoreAttribute.PublicKey.ordinal)
        assertEquals(CoreAttribute.PublicKey.spec.protoAttribute, CoreAttribute.PublicKey.spec.protoAttribute)

        assertEquals(5, CoreAttribute.Name.ordinal)
        assertEquals(CoreAttribute.Name.spec.protoAttribute, CoreAttribute.Name.spec.protoAttribute)

        assertEquals(6, CoreAttribute.Description.ordinal)
        assertEquals(CoreAttribute.Description.spec.protoAttribute, CoreAttribute.Description.spec.protoAttribute)

        assertEquals(7, CoreAttribute.Text.ordinal)
        assertEquals(CoreAttribute.Text.spec.protoAttribute, CoreAttribute.Text.spec.protoAttribute)

        assertEquals(8, CoreAttribute.ImageUri.ordinal)
        assertEquals(CoreAttribute.ImageUri.spec.protoAttribute, CoreAttribute.ImageUri.spec.protoAttribute)

        assertEquals(9, CoreAttribute.Tags.ordinal)
        assertEquals(CoreAttribute.Tags.spec.protoAttribute, CoreAttribute.Tags.spec.protoAttribute)

        assertEquals(10, CoreAttribute.Trust.ordinal)
        assertEquals(CoreAttribute.Trust.spec.protoAttribute, CoreAttribute.Trust.spec.protoAttribute)

        assertEquals(11, CoreAttribute.Like.ordinal)
        assertEquals(CoreAttribute.Like.spec.protoAttribute, CoreAttribute.Like.spec.protoAttribute)

        assertEquals(12, CoreAttribute.Rating.ordinal)
        assertEquals(CoreAttribute.Rating.spec.protoAttribute, CoreAttribute.Rating.spec.protoAttribute)

        assertEquals(13, CoreAttribute.ParentId.ordinal)
        assertEquals(CoreAttribute.ParentId.spec.protoAttribute, CoreAttribute.ParentId.spec.protoAttribute)

        assertEquals(14, CoreAttribute.CommentIds.ordinal)
        assertEquals(CoreAttribute.CommentIds.spec.protoAttribute, CoreAttribute.CommentIds.spec.protoAttribute)

        assertEquals(15, CoreAttribute.NetworkToken.ordinal)
        assertEquals(CoreAttribute.NetworkToken.spec.protoAttribute, CoreAttribute.NetworkToken.spec.protoAttribute)

        assertEquals(16, CoreAttribute.AttachmentIds.ordinal)
        assertEquals(CoreAttribute.AttachmentIds.spec.protoAttribute, CoreAttribute.AttachmentIds.spec.protoAttribute)

        assertEquals(17, CoreAttribute.TopLevelParentId.ordinal)
        assertEquals(CoreAttribute.TopLevelParentId.spec.protoAttribute, CoreAttribute.TopLevelParentId.spec.protoAttribute)

        assertEquals(18, CoreAttribute.OriginDistance.ordinal)
        assertEquals(CoreAttribute.OriginDistance.spec.protoAttribute, CoreAttribute.OriginDistance.spec.protoAttribute)

        assertEquals(19, CoreAttribute.entries.toTypedArray().count())
    }
}