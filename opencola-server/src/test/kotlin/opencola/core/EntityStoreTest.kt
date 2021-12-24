import opencola.core.model.UNCOMMITTED
import opencola.core.storage.EntityStore
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EntityStoreTest {
    @Test
    fun testEntityStore() {
        val authority = getAuthority()
        val factFile = createTempFile("entity-store", ".fct")

        val store = EntityStore(listOf(authority).toSet())
        store.load(factFile)
        val entity = getActorEntity(authority.entityId)
        store.updateEntity(authority, entity)

        val store2 = EntityStore(listOf(authority).toSet())
        store2.load(factFile)
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
}