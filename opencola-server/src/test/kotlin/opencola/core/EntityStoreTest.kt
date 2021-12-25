import opencola.core.model.ActorEntity
import opencola.core.model.UNCOMMITTED
import opencola.core.storage.EntityStore
import java.nio.file.Path
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EntityStoreTest {
    fun createTempTransactionFile(): Path {
        return createTempFile("entity-store", ".fct")
    }
    @Test
    fun testEntityStore() {
        val authority = getAuthority()
        val transactionFile = createTempTransactionFile()

        val store = EntityStore(listOf(authority).toSet())
        store.load(transactionFile)
        val entity = getActorEntity(authority.entityId)
        store.updateEntity(authority, entity)

        val store2 = EntityStore(listOf(authority).toSet())
        store2.load(transactionFile)
        val entity2 = store2.getEntity(authority, entity.entityId)
            ?: throw RuntimeException("Entity could not be reloaded from store")

        entity.getFacts().zip(entity2.getFacts()).forEach {
            assertEquals(it.first.authorityId, it.second.authorityId)
            assertEquals(it.first.entityId, it.second.entityId)
            assertEquals(it.first.attribute, it.second.attribute)
            assertContentEquals(it.first.value, it.second.value)
            assertEquals(it.first.add, it.second.add)
            // Transaction id changes on commit, so we don't expect them to be the same
            assertEquals(UNCOMMITTED, it.first.transactionId)
            assertEquals(0, it.second.transactionId)
        }
    }

    @Test
    fun testUpdateAfterReload(){
        val authority = getAuthority()
        val transactionFile = createTempTransactionFile()

        val store = EntityStore(listOf(authority).toSet())
        store.load(transactionFile)
        val entity = getActorEntity(authority.entityId)
        store.updateEntity(authority, entity)

        val store1 = EntityStore(listOf(authority).toSet())
        store1.load(transactionFile)
        val entity1 = store.getEntity(authority, entity.entityId) as ActorEntity
        entity1.name = "new name"
        store.updateEntity(authority, entity1)

        val store2 = EntityStore(listOf(authority).toSet())
        store2.load(transactionFile)
        val entity2 = store.getEntity(authority, entity.entityId) as ActorEntity
        assertEquals(entity2.name, "new name")
    }
}