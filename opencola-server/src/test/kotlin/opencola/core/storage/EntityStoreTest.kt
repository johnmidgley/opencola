package opencola.core.storage

import opencola.core.getActorEntity
import opencola.core.TestApplication
import opencola.core.config.getApplications
import opencola.core.content.TextExtractor
import opencola.core.event.EventBus
import opencola.core.model.*
import opencola.core.security.Signator
import opencola.core.storage.EntityStore.*
import org.kodein.di.instance
import java.net.URI
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EntityStoreTest {
    private val app = TestApplication.instance
    private val authority by app.injector.instance<Authority>()
    private val eventBus by app.injector.instance<EventBus>()
    private val signator by app.injector.instance<Signator>()
    private val fileStore by app.injector.instance<FileStore>()
    private val textExtractor by app.injector.instance<TextExtractor>()
    private val addressBook by app.injector.instance<AddressBook>()
    // TODO: Make .txs and .db use the test run folder - currently save directly in the test folder
    private val simpleEntityStorePath = app.config.storage.path.resolve("${TestApplication.testRunName}.txs")
    private val getSimpleEntityStore = { SimpleEntityStore(simpleEntityStorePath, eventBus, fileStore, textExtractor, addressBook, authority, signator) }
    private val sqLiteEntityStorePath = app.config.storage.path.resolve("${TestApplication.testRunName}.db")
    private val getSQLiteEntityStore = { ExposedEntityStore(authority, eventBus, fileStore, textExtractor, addressBook, signator, SQLiteDB(sqLiteEntityStorePath).db) }

    // TODO: Remove these and switch to functions below
    init{
        getSimpleEntityStore().resetStore()
        getSQLiteEntityStore().resetStore()
    }

    private fun getFreshSimpleEntityStore(): SimpleEntityStore {
        return SimpleEntityStore(TestApplication.getTmpFilePath(".txs"), eventBus, fileStore, textExtractor, addressBook, authority, signator)
    }

    private fun getFreshExposeEntityStore(): ExposedEntityStore {
        return ExposedEntityStore(authority, eventBus, fileStore, textExtractor, addressBook, signator, SQLiteDB(TestApplication.getTmpFilePath(".db")).db)
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
        store.updateEntities(entity)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(authority.authorityId, entity.entityId)
            ?: throw RuntimeException("Entity could not be reloaded from store")

        entity.getFacts().zip(entity2.getFacts()).forEach {
            assertEquals(it.first.authorityId, it.second.authorityId)
            assertEquals(it.first.entityId, it.second.entityId)
            assertEquals(it.first.attribute, it.second.attribute)
            assertEquals(it.first.value, it.second.value)
            assertEquals(it.first.operation, it.second.operation)
            assertEquals(it.first.transactionOrdinal, it.second.transactionOrdinal)
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
        store.updateEntities(entity)

        val store1 = getEntityStore()
        val entity1 = store1.getEntity(authority.authorityId, entity.entityId) as ActorEntity
        entity1.name = "new name"
        store.updateEntities(entity1)

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

    private fun testGetTransaction(entityStore: EntityStore){
        val entity = ResourceEntity(authority.authorityId, URI("http://opencola.org"))
        val epochSecond = Instant.now().epochSecond
        val signedTransaction = entityStore.updateEntities(entity)
        assertNotNull(signedTransaction)
        assert(signedTransaction.transaction.epochSecond >= epochSecond)

        val transaction = entityStore.getTransaction(signedTransaction.transaction.id)
        assertNotNull(transaction)

        val transactionsFromNull = entityStore.getSignedTransactions(listOf(authority.authorityId), null, TransactionOrder.Ascending, 100)
        assertNotNull(transactionsFromNull.firstOrNull{ it.transaction.id == transaction.transaction.id})
    }

    @Test
    fun testGetTransactionsSimple() {
        testGetTransactions(getFreshSimpleEntityStore())
    }

    @Test
    fun testGetTransactionsExposed() {
        testGetTransactions(getFreshExposeEntityStore())
    }

    private fun testGetTransactions(entityStore: EntityStore){
        val entities = (0 until 3).map { ResourceEntity(authority.authorityId, URI("http://test/$it")) }
        val transactions = entities.map{ entityStore.updateEntities(it)!! }
        val transactionIds = transactions.map{ it.transaction.id }

        val firstTransaction = entityStore.getSignedTransactions(listOf(authority.authorityId), null, TransactionOrder.Ascending, 1).firstOrNull()
        assertNotNull(firstTransaction)
        assertEquals(transactions.first(), firstTransaction)

        val firstTransactionAll = entityStore.getSignedTransactions(emptyList(), null, TransactionOrder.Ascending, 1).firstOrNull()
        assertNotNull(firstTransactionAll)
        assertEquals(transactions.first(), firstTransaction)

        val lastTransaction = entityStore.getSignedTransactions(listOf(authority.authorityId), null, TransactionOrder.Descending, 1).firstOrNull()
        assertNotNull(lastTransaction)
        assertEquals(entities.last().entityId, lastTransaction.transaction.transactionEntities.first().entityId)

        val lastTransactionAll = entityStore.getSignedTransactions(emptyList(), null, TransactionOrder.Descending, 1).firstOrNull()
        assertNotNull(lastTransactionAll)
        assertEquals(transactions.last(), lastTransactionAll)

        val middleTransactionsForward = entityStore.getSignedTransactions(listOf(authority.authorityId), transactionIds[1], TransactionOrder.Ascending, 10)
        assertEquals(transactions.drop(1), middleTransactionsForward)

        val middleTransactionsBackward = entityStore.getSignedTransactions(listOf(authority.authorityId), transactionIds[1], TransactionOrder.Descending, 10)
        assertEquals(transactions.reversed().drop(1), middleTransactionsBackward)

        val allTransactionsForward = entityStore.getSignedTransactions(emptyList(), null, TransactionOrder.Ascending, 10)
        assertEquals(transactions, allTransactionsForward)

        val allTransactionsBackward = entityStore.getSignedTransactions(emptyList(), null, TransactionOrder.Descending, 10)
        assertEquals(transactions.reversed(), allTransactionsBackward)

        // TODO - Add tests across AuthorityIds
    }

    // TODO: This only tests the entity store that is used in the test config (i.e. SimpleEntityStore is not tested)
    @Test
    fun testGetFacts(){
        val applications = getApplications(2, TestApplication.config, 6000)

        // Create some entities for first authority
        val authority0 by applications[0].injector.instance<Authority>()
        val entityStore0 by applications[0].injector.instance<EntityStore>()
        val entities0 = (0 until 2).map { ResourceEntity(authority0.authorityId, URI("http://test/$it")) }
        entityStore0.updateEntities(*entities0.toTypedArray<Entity>())

        // Add some entities from peer store with same entity ids
        val authority1 by applications[1].injector.instance<Authority>()
        val entityStore1 by applications[1].injector.instance<EntityStore>()
        val entities1 = (0 until 2).map { ResourceEntity(authority1.authorityId, URI("http://test/$it")) }
        val transaction = entityStore1.updateEntities(*entities1.toTypedArray<Entity>()) ?: throw RuntimeException("Unable to update entities")
        entityStore0.addSignedTransactions(listOf(transaction))

        val authority0Facts = entityStore0.getFacts(listOf(authority0.authorityId), emptyList())
        assert(authority0Facts.isNotEmpty())
        assert(authority0Facts.all{ it.authorityId == authority0.authorityId} )
        assertNotNull(authority0Facts.firstOrNull{ it.entityId == entities0[0].entityId})
        assertNotNull(authority0Facts.firstOrNull{ it.entityId == entities0[1].entityId})

        val authority1Facts = entityStore0.getFacts(listOf(authority1.authorityId), emptyList())
        assert(authority1Facts.isNotEmpty())
        assert(authority1Facts.all{ it.authorityId == authority1.authorityId} )
        assertNotNull(authority1Facts.firstOrNull{ it.entityId == entities1[0].entityId})
        assertNotNull(authority1Facts.firstOrNull{ it.entityId == entities1[1].entityId})

        val entity0Facts = entityStore0.getFacts(emptyList(), listOf(entities0[0].entityId))
        assert(entity0Facts.isNotEmpty())
        assertTrue { entity0Facts.all { it.entityId == entities0[0].entityId } }
        assertTrue(entity0Facts.any{ it.authorityId == authority0.authorityId} )
        assertTrue(entity0Facts.any{ it.authorityId == authority1.authorityId} )

        val entity1Facts = entityStore0.getFacts(emptyList(), listOf(entities0[1].entityId))
        assert(entity1Facts.isNotEmpty())
        assertTrue { entity1Facts.all { it.entityId == entities0[1].entityId } }
        assertTrue(entity1Facts.any{ it.authorityId == authority0.authorityId} )
        assertTrue(entity1Facts.any{ it.authorityId == authority1.authorityId} )
    }
}