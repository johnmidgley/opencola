package opencola.core.storage

import opencola.core.getActorEntity
import opencola.core.TestApplication
import opencola.core.model.ActorEntity
import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.model.UNCOMMITTED
import opencola.core.security.Signator
import org.kodein.di.instance
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EntityStoreTest {
    private val app = TestApplication.instance
    private val authority by app.injector.instance<Authority>()
    private val signator by app.injector.instance<Signator>()
    // TODO: Make .txs and .db use the test run folder - currently save directly in the test folder
    private val simpleEntityStorePath = TestApplication.testRunStoragePath.resolve("${TestApplication.testRunName}.txs")
    private val getSimpleEntityStore = { SimpleEntityStore(simpleEntityStorePath, authority, signator) }
    private val sqLiteEntityStorePath = TestApplication.testRunStoragePath.resolve("${TestApplication.testRunName}.db")
    private val getSQLiteEntityStore = { ExposedEntityStore(authority, signator, SQLiteDB(sqLiteEntityStorePath).db) }

    // TODO: Remove these and switch to functions below
    init{
        getSimpleEntityStore().resetStore()
        getSQLiteEntityStore().resetStore()
    }

    fun getFreshSimpleEntityStore(): SimpleEntityStore {
        return SimpleEntityStore(TestApplication.getTmpFilePath(".txs"), authority, signator)
    }

    fun getFreshExposeEntityStore(): ExposedEntityStore {
        return ExposedEntityStore(authority, signator, SQLiteDB(TestApplication.getTmpFilePath(".db")).db)
    }

    @Test
    fun testEntityStoreSimple(){
        testEntityStore(authority, getSimpleEntityStore)
    }

    @Test
    fun testEntityStoreSQLite(){
        testEntityStore(authority, getSQLiteEntityStore)
    }

    private fun testEntityStore(authority: Authority, getEntityStore: ()-> EntityStore) {
        val store = getEntityStore()
        val entity = getActorEntity(authority.entityId)
        store.commitChanges(entity)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(authority.authorityId, entity.entityId)
            ?: throw RuntimeException("Entity could not be reloaded from store")

        entity.getFacts().zip(entity2.getFacts()).forEach {
            assertEquals(it.first.authorityId, it.second.authorityId)
            assertEquals(it.first.entityId, it.second.entityId)
            assertEquals(it.first.attribute, it.second.attribute)
            assertEquals(it.first.value, it.second.value)
            assertEquals(it.first.operation, it.second.operation)
            // Transaction id changes on commit, so we don't expect them to be the same
            assertEquals(UNCOMMITTED, it.first.transactionId)
            assertEquals(1, it.second.transactionId)
        }
    }

    @Test
    fun testUpdateAfterReloadSimple(){
        testUpdateAfterReload(authority, getSimpleEntityStore)
    }

    @Test
    fun testUpdateAfterReloadSQLite(){
        testUpdateAfterReload(authority, getSQLiteEntityStore)
    }

    private fun testUpdateAfterReload(authority: Authority, getEntityStore: ()-> EntityStore){
        val store = getEntityStore()
        val entity = getActorEntity(authority.entityId)
        store.commitChanges(entity)

        val store1 = getEntityStore()
        val entity1 = store1.getEntity(authority.authorityId, entity.entityId) as ActorEntity
        entity1.name = "new name"
        store.commitChanges(entity1)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(authority.authorityId, entity.entityId) as ActorEntity
        assertEquals(entity2.name, "new name")
    }

    @Test
    fun testGetTransactionSimple(){
        testGetTransaction(getFreshSimpleEntityStore())
    }

    @Test
    fun testGetTransactionExposed() {
        testGetTransaction(getFreshExposeEntityStore())
    }

    fun testGetTransaction(entityStore: EntityStore){
        val entity = ResourceEntity(authority.authorityId, URI("http://opencola.org"))
        entityStore.commitChanges(entity)
        val transaction = entityStore.getTransaction(authority.authorityId, 1)

        assertNotNull(transaction)
    }
}