package opencola.core

import getActorEntity
import getAuthority
import opencola.core.model.ActorEntity
import opencola.core.model.Authority
import opencola.core.model.Entity
import opencola.core.model.UNCOMMITTED
import opencola.core.storage.EntityStore
import opencola.core.storage.ExposedEntityStore
import opencola.core.storage.PostgresDb
import opencola.core.storage.SimpleEntityStore
import opencola.server.storagePath
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityStoreTest {
    private val authority = getAuthority()
    private val storagePath = Path(System.getProperty("user.dir"), "..", "storage")
    private val entityStorePath: Path = storagePath.resolve("${authority.authorityId}.test.txs")
    private val getSimpleEntityStore = { SimpleEntityStore(authority, entityStorePath) }
    private val getPostgresEntityStore = { ExposedEntityStore(authority, PostgresDb.db) }

    private fun createTempTransactionFile(): Path {
        return createTempFile("entity-store", ".fct")
    }

    init{
        getPostgresEntityStore().resetStore()
        getSimpleEntityStore().resetStore()
    }

    @Test
    fun testEntityStoreSimple(){
        testEntityStore(getAuthority(), getSimpleEntityStore)
    }

    @Test
    fun testEntityStorePostgres(){
        testEntityStore(authority, getPostgresEntityStore)
    }

    private fun testEntityStore(authority: Authority, getEntityStore: ()-> EntityStore) {
        val store = getEntityStore()
        val entity = getActorEntity(authority.entityId)
        store.commitChanges(entity)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(authority, entity.entityId)
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
        testUpdateAfterReload(getAuthority(), getSimpleEntityStore)
    }

    @Test
    fun testUpdateAfterReloadPostGres(){
        testUpdateAfterReload(authority, getPostgresEntityStore)
    }

    private fun testUpdateAfterReload(authority: Authority, getEntityStore: ()-> EntityStore){
        val store = getEntityStore()
        val entity = getActorEntity(authority.entityId)
        store.commitChanges(entity)

        val store1 = getEntityStore()
        val entity1 = store1.getEntity(authority, entity.entityId) as ActorEntity
        entity1.name = "new name"
        store.commitChanges(entity1)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(authority, entity.entityId) as ActorEntity
        assertEquals(entity2.name, "new name")
    }
}