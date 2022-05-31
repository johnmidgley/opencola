package opencola.server.handlers

import opencola.core.TestApplication
import opencola.core.model.*
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
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
        val peerAuthority by peerApplication.injector.instance<Authority>()
        val peerEntity = getEntity(peerAuthority.authorityId)
        val peerEntityStore by peerApplication.injector.instance<EntityStore>()
        val peerTransaction = peerEntityStore.updateEntities(peerEntity)
        assertNotNull(peerTransaction)

        // Add resource to local store
        val injector = TestApplication.instance.injector
        val localAuthority by injector.instance<Authority>()
        val localEntityStore by injector.instance<EntityStore>()
        val localAddressBook by injector.instance<AddressBook>()
        localAddressBook.updateAuthority(Authority(localAuthority.authorityId, peerAuthority.publicKey!!, peerAuthority.uri!!, peerAuthority.name!!))
        localEntityStore.addSignedTransactions(listOf(peerTransaction))
        assertNull(localEntityStore.getEntity(localAuthority.authorityId, peerEntity.entityId))

        // Save and check that copy worked
        val saveEntity = getOrCopyEntity(localAuthority.authorityId, localEntityStore, peerEntity.entityId)
        assertNotNull(saveEntity)
        localEntityStore.updateEntities(saveEntity)
        val localEntity = localEntityStore.getEntity(localAuthority.authorityId, peerEntity.entityId)
        assertNotNull(localEntity)
        assertEquals(localAuthority.authorityId, localEntity.authorityId)
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
}