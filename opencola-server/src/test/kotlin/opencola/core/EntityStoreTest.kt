import opencola.core.storage.EntityStore
import kotlin.io.path.createTempFile
import kotlin.test.Test
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
        assertEquals(entity, entity2)
    }
}