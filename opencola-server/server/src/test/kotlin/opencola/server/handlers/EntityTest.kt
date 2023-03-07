package opencola.server.handlers

import io.opencola.model.*
import opencola.core.TestApplication
import io.opencola.storage.AddressBook
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.EntityStore
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class EntityTest {
    private fun saveEntity(getEntity: (Id) -> Entity): Entity {
        // Make a peer transaction that contains a resource
        val peerApplication = TestApplication.newApplication()
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
        val saveEntity = getOrCopyEntity(localPersona.personaId, localEntityStore, peerEntity.entityId)
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
    fun testGetOrCopyResource(){
        val uri = URI("https://test")
        val localResource = saveEntity { ResourceEntity(it, uri, "name") } as ResourceEntity
        assertEquals(uri, localResource.uri)
    }

    @Test
    fun testGetOrCopyPost(){
        saveEntity { PostEntity(it, "name", "description") } as PostEntity
    }

    @Test
    fun testDeleteEntityWithDependents() {
        val app  = TestApplication.instance
        val persona = TestApplication.instance.getPersonas().first()
        val entityStore = app.inject<EntityStore>()
        val addressBook = app.inject<AddressBook>()

        // Make a post with a comment
        val post = PostEntity(persona.personaId, "name", "description")
        val comment = CommentEntity(persona.personaId, post.entityId, "comment")
        entityStore.updateEntities(post, comment)
        assertNotNull(entityStore.getEntity(persona.personaId, post.entityId))
        assertNotNull(entityStore.getEntity(persona.personaId, comment.entityId))

        // Delete the post and check that the comment is gone
        deleteEntity(entityStore, addressBook, persona, post.entityId)
        assertNull(entityStore.getEntity(persona.personaId, post.entityId))
        assertNull(entityStore.getEntity(persona.personaId, comment.entityId))
    }
}