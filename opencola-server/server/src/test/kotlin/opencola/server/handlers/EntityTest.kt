package opencola.server.handlers

import io.opencola.model.*
import io.opencola.application.TestApplication
import io.opencola.event.bus.EventBus
import io.opencola.storage.addPersona
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EntityTest {
    private fun saveEntity(getEntity: (Id) -> Entity): Entity {
        // Make a peer transaction that contains a resource
        val peerApplication = TestApplication.newApplication().also { it.open() }
        val peerPersona = peerApplication.getPersonas().first()
        val peerEntity = getEntity(peerPersona.personaId)
        val peerEntityStore by peerApplication.injector.instance<EntityStore>()
        val peerTransaction = peerEntityStore.updateEntities(peerEntity)
        assertNotNull(peerTransaction)

        // Add resource to local store
        val injector = TestApplication.instance.injector
        val localPersona = TestApplication.instance.getPersonas().first()
        val localEntityStore by injector.instance<EntityStore>()
        val localAddressBook by injector.instance<AddressBook>()

        localAddressBook.updateEntry(
            AddressBookEntry(
                localPersona.personaId,
                peerPersona.entityId,
                peerPersona.name,
                peerPersona.publicKey,
                peerPersona.address,
                peerPersona.imageUri,
                peerPersona.isActive

            )
        )

        localEntityStore.addSignedTransactions(listOf(peerTransaction))
        assertNull(localEntityStore.getEntity(localPersona.personaId, peerEntity.entityId))

        // Save and check that copy worked
        val saveEntity =
            getOrCopyEntity(localAddressBook, localEntityStore, localPersona.personaId, peerEntity.entityId)
        assertNotNull(saveEntity)
        localEntityStore.updateEntities(saveEntity)
        val localEntity = localEntityStore.getEntity(localPersona.personaId, peerEntity.entityId)
        assertNotNull(localEntity)
        assertEquals(localPersona.personaId, localEntity.authorityId)
        assertEquals(peerEntity.entityId, localEntity.entityId)
        assertEquals(peerEntity.name, localEntity.name)
        assertEquals(peerEntity.description, localEntity.description)
        assertEquals(peerEntity.text, localEntity.text)
        assertEquals(peerEntity.imageUri, localEntity.imageUri)
        assertEquals(peerEntity.trust, localEntity.trust)
        assertEquals(peerEntity.like, localEntity.like)
        assertEquals(peerEntity.rating, localEntity.rating)
        assertEquals(peerEntity.tags, localEntity.tags)
        assertEquals(peerEntity.commentIds, localEntity.commentIds)

        return localEntity
    }

    @Test
    fun testGetOrCopyResource() {
        val uri = URI("https://test")
        val localResource = saveEntity { ResourceEntity(it, uri, "name") } as ResourceEntity
        assertEquals(uri, localResource.uri)
    }

    @Test
    fun testGetOrCopyPost() {
        saveEntity { PostEntity(it, "name", "description") } as PostEntity
    }

    @Test
    fun testOriginDistance() {
        // Test that a post from a peer with no origin distance is set to 1
        val entity0 = saveEntity { PostEntity(it, "name0", "description0") } as PostEntity
        assertEquals(1, entity0.originDistance)

        // Test that a post from a peer with an origin distance incremented
        val entity1 = saveEntity { authorityId ->
            PostEntity(authorityId, "name1", "description1").also { it.originDistance = 2 }
        } as PostEntity
        assertEquals(3, entity1.originDistance)

        // Test that saving a post from another peer results in a null origin distance (i.e. equivalent to 0)
        val app = TestApplication.instance
        val addressBook = app.inject<AddressBook>()
        val entityStore = app.inject<EntityStore>()
        val persona0 = addressBook.addPersona("Persona0")
        val persona1 = addressBook.addPersona("Persona1")
        val postEntity = PostEntity(persona0.personaId, "name", "description")
        entityStore.updateEntities(postEntity)
        val entity2 = getOrCopyEntity(addressBook, entityStore, persona1.personaId, postEntity.entityId) as PostEntity
        assertEquals(null, entity2.originDistance)

        val result = newPost(
            app.inject(),
            app.inject(),
            app.inject(),
            app.inject(),
            app.inject(),
            Context(""),
            persona0,
            EntityPayload(description = "Test"),
            emptySet()
        )!!

        assertNull(result.summary.originDistance)
    }

    @Test
    fun testDeleteEntityWithDependents() {
        val app = TestApplication.instance
        val persona = TestApplication.instance.getPersonas().first()
        val entityStore = app.inject<EntityStore>()
        val addressBook = app.inject<AddressBook>()
        val eventBus = app.inject<EventBus>()

        // Make a post with a comment
        val post = PostEntity(persona.personaId, "name", "description")
        val comment = CommentEntity(persona.personaId, post.entityId, "comment")
        entityStore.updateEntities(post, comment)
        assertNotNull(entityStore.getEntity(persona.personaId, post.entityId))
        assertNotNull(entityStore.getEntity(persona.personaId, comment.entityId))

        // Delete the post and check that the comment is gone
        deleteEntity(entityStore, addressBook, eventBus, app.inject(), Context(""), persona, post.entityId)
        assertNull(entityStore.getEntity(persona.personaId, post.entityId))
        assertNull(entityStore.getEntity(persona.personaId, comment.entityId))
    }
}