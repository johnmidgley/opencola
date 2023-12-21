package io.opencola.model

import io.opencola.model.value.EmptyValue
import io.opencola.model.value.IdValue
import io.opencola.security.generateKeyPair
import java.net.URI
import kotlin.test.*

class EntityTest {
    @Test
    fun testAuthorityEntity() {
        val uri = URI("opencola://12345")
        val imageUri = URI("http://images/123456")
        val name = "name"
        val description = "description"
        val publicKey = generateKeyPair().public
        val like = true
        val trust = .75F
        val tags = listOf("this", "that")
        val rating = .37F
        val networkToken = "networkToken".toByteArray()

        val entity = Authority(publicKey, URI("https://authority"), "Name")
        entity.uri = uri
        assertEquals(uri, entity.uri)

        entity.imageUri = imageUri
        assertEquals(imageUri, entity.imageUri)

        entity.name = name
        assertEquals(name, entity.name)

        entity.description = description
        assertEquals(description, entity.description)

        entity.publicKey = publicKey
        assertEquals(publicKey, entity.publicKey)

        entity.like = like
        assertEquals(like, entity.like)

        entity.trust = trust
        assertEquals(trust, entity.trust)

        entity.tags = tags
        assertEquals(tags, entity.tags)

        entity.rating = rating
        assertEquals(rating, entity.rating)

        entity.like = null
        assertNull(entity.like)

        entity.networkToken = networkToken
        assertContentEquals(networkToken, entity.networkToken)
    }

    @Test
    fun testComments() {
        val authorityId = Id.ofData("".toByteArray())
        val entity = ResourceEntity(authorityId, URI("https://test.com"))
        val comment1 = CommentEntity(authorityId, entity.entityId, "comment1")

        entity.commitFacts(0, 0)
        val comment1Facts = comment1.commitFacts(1, 1)
        assertEquals(comment1.authorityId, authorityId)
        assertEquals(comment1.text, "comment1")

        val computedFact1 = computeEntityCommentIds(comment1Facts).single()
        assertEquals(computedFact1.authorityId, authorityId)
        assertEquals(computedFact1.entityId, entity.entityId)
        assertEquals(computedFact1.attribute, CoreAttribute.CommentIds.spec)
        assertEquals(computedFact1.value as IdValue, IdValue(comment1.entityId))
        assertEquals(computedFact1.operation, Operation.Add)
    }

    @Test
    fun testAttachments() {
        val authorityId = Id.ofData("".toByteArray())
        val entity = ResourceEntity(authorityId, URI("https://test.com"))

        val attachmentId1 = Id.ofData("attachment1".toByteArray())
        val attachmentId2 = Id.ofData("attachment2".toByteArray())

        entity.attachmentIds = listOf(attachmentId1, attachmentId2, attachmentId1)

        assertEquals(2, entity.attachmentIds.size)
        assertContains(entity.attachmentIds, attachmentId1)
        assertContains(entity.attachmentIds, attachmentId2)
    }

    @Test
    fun testSettingSameValue() {
        val uri = URI("opencola://test-resource")
        val entity = ResourceEntity(Id.ofUri(uri), uri)

        assertEquals(1, entity.getAllFacts().filter { it.attribute == CoreAttribute.Uri.spec }.size)
        assertFails { entity.uri = URI("opencola://different-uri") }

        // val authority = TestApplication.instance.getPersonas().first()
        // val store = EntityStore(authority)
        //TODO: Fix entity store so adding an entity returns a new Entity, and commit occurs at the same time
        // then test here that setting a new value results in a new fact
    }

    private fun fact(
        authorityId: Id,
        entityId: Id,
        attribute: CoreAttribute,
        value: Any,
        operation: Operation,
        epochSecond: Long,
        transactionOrdinal: Long = epochSecond
    ): Fact {
        val wrappedValue = if (value == EmptyValue) EmptyValue else attribute.spec.valueWrapper.wrap(value)
        return Fact(
            authorityId,
            entityId,
            attribute.spec,
            wrappedValue,
            operation,
            epochSecond,
            transactionOrdinal
        )
    }

    @Test
    fun testGetCurrentFacts() {
        val authorityId = Id.new()
        val entityId = Id.new()

        val facts = listOf(
            // An untouched value that was added once
            fact(authorityId, entityId, CoreAttribute.Uri, URI("https://test.com"), Operation.Add, 0),

            // A value that was added and then removed
            fact(authorityId, entityId, CoreAttribute.Text, "text", Operation.Add, 1),
            fact(authorityId, entityId, CoreAttribute.Text, EmptyValue, Operation.Retract, 2),

            // A value that was added and then changed
            fact(authorityId, entityId, CoreAttribute.Like, true, Operation.Add, 3),
            fact(authorityId, entityId, CoreAttribute.Like, false, Operation.Add, 4),

            // A value that was added and then removed, then re-added, with another value added in between
            fact(authorityId, entityId, CoreAttribute.Name, "Name", Operation.Add, 5),
            fact(authorityId, entityId, CoreAttribute.Description, "Desc", Operation.Add, 6),
            fact(authorityId, entityId, CoreAttribute.Name, EmptyValue, Operation.Retract, 7),
            fact(authorityId, entityId, CoreAttribute.Name, "Name2", Operation.Add, 8),
        )

        val currentFacts = (Entity.fromFacts(facts) as RawEntity).getCurrentFacts()
        assertEquals(4, currentFacts.size)
        assertEquals(facts[0], currentFacts[0])
        assertEquals(facts[4], currentFacts[1])
        assertEquals(facts[6], currentFacts[2])
        assertEquals(facts[8], currentFacts[3])

        // Test multi value set property (attachmentIds)
        val attachmentIds = listOf(Id.new(), Id.new())
        val retractedId = Id.new()
        val attachmentFacts = listOf(
            fact(authorityId, entityId, CoreAttribute.AttachmentIds, attachmentIds[0], Operation.Add, 0),
            fact(authorityId, entityId, CoreAttribute.AttachmentIds, attachmentIds[1], Operation.Add, 0),
            fact(authorityId, entityId, CoreAttribute.AttachmentIds, retractedId, Operation.Add, 0),
            fact(authorityId, entityId, CoreAttribute.AttachmentIds, retractedId, Operation.Retract, 1),
        )
        val attachmentEntity = Entity.fromFacts(attachmentFacts) as RawEntity
        assertEquals(4, attachmentEntity.getAllFacts().size)
        assertEquals(2, attachmentEntity.getCurrentFacts().size)
        assertEquals(attachmentIds, attachmentEntity.attachmentIds)
    }

    @Test
    fun testRawEntity() {
        // Create an entity with no type directly
        val authorityId = Id.new()
        val entityId = Id.new()
        val rawEntity = RawEntity(authorityId, entityId)
        rawEntity.like = true
        rawEntity.commitFacts(System.currentTimeMillis(), 0)
        val facts = rawEntity.getAllFacts()
        assertEquals(1, facts.size)

        // Create an entity with no type from facts
        val entity = Entity.fromFacts(facts) as RawEntity
        assertEquals(true, entity.like)

        // Remove all current facts
        entity.like = null
        entity.commitFacts(System.currentTimeMillis(), 1)
        assertEquals(2, entity.getAllFacts().size)
        assertEquals(0, entity.getCurrentFacts().size)
        assertNull(Entity.fromFacts(entity.getAllFacts()))

        val entity1 = RawEntity(authorityId, entityId)
        entity1.attachmentIds = listOf(Id.new(), Id.new())
        entity1.commitFacts(System.currentTimeMillis(), 0)
        val entity2 = Entity.fromFacts(entity1.getAllFacts()) as RawEntity
        assertEquals(entity1.attachmentIds, entity2.attachmentIds)
    }
}