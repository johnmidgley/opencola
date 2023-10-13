package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.application.getApplications
import io.opencola.event.EventBus
import io.opencola.model.*
import io.opencola.model.value.EmptyValue
import io.opencola.security.Signator
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.db.getSQLiteDB
import io.opencola.storage.entitystore.*
import io.opencola.util.CompressionFormat
import org.kodein.di.instance
import java.net.URI
import java.time.Instant
import kotlin.test.*

class EntityStoreTest {
    private val app = TestApplication.instance

    // TODO: Make EntityStoreContext, similar to NetworkNodeContext
    private val eventBus by app.injector.instance<EventBus>()
    private val signator by app.injector.instance<Signator>()
    private val addressBook by app.injector.instance<AddressBook>()
    private val persona = addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>().single()
    private val sqLiteEntityStorePath = app.storagePath.resolve("${TestApplication.testRunName}.db")
    private val getSQLiteEntityStore =
        {
            ExposedEntityStoreV2(
                "entity-store",
                sqLiteEntityStorePath,
                ::getSQLiteDB,
                Attributes.get(),
                signator,
                addressBook,
                eventBus
            )
        }

    private fun getFreshExposeEntityStore(): ExposedEntityStoreV2 {
        return ExposedEntityStoreV2(
            "entity-store",
            TestApplication.getTmpDirectory("entity-store"),
            ::getSQLiteDB,
            Attributes.get(),
            signator,
            addressBook,
            eventBus
        )
    }

    @Test
    fun testEntityStoreSQLite() {
        testEntityStore(persona, getSQLiteEntityStore)
    }

    private fun testEntityStore(persona: PersonaAddressBookEntry, getEntityStore: () -> EntityStore) {
        val store = getEntityStore()
        val entity = getAuthorityEntity(persona.entityId)
        store.updateEntities(entity)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(persona.personaId, entity.entityId)
            ?: throw RuntimeException("Entity could not be reloaded from store")

        entity.getAllFacts().zip(entity2.getAllFacts()).forEach {
            assertEquals(it.first.authorityId, it.second.authorityId)
            assertEquals(it.first.entityId, it.second.entityId)
            assertEquals(it.first.attribute, it.second.attribute)
            assertEquals(it.first.value, it.second.value)
            assertEquals(it.first.operation, it.second.operation)
            assertEquals(it.first.transactionOrdinal, it.second.transactionOrdinal)
        }

        store2.deleteEntities(persona.personaId, entity.entityId)
        assertEquals(null, store2.getEntity(persona.personaId, entity.entityId))
    }

    @Test
    fun testUpdateAfterReloadSQLite() {
        testUpdateAfterReload(persona, getSQLiteEntityStore)
    }

    private fun testUpdateAfterReload(persona: PersonaAddressBookEntry, getEntityStore: () -> EntityStore) {
        val store = getEntityStore()
        val entity = getAuthorityEntity(persona.entityId)
        store.updateEntities(entity)

        val store1 = getEntityStore()
        val entity1 = store1.getEntity(persona.personaId, entity.entityId) as Authority
        val newName = "new name".also { entity1.name = it }
        val networkToken = "token".toByteArray().also { entity1.networkToken = it }
        store.updateEntities(entity1)

        val store2 = getEntityStore()
        val entity2 = store2.getEntity(persona.personaId, entity.entityId) as Authority
        assertEquals(entity2.name, newName)
        assertContentEquals(entity2.networkToken, networkToken)
    }

    @Test
    fun testGetTransactionExposed() {
        testGetTransaction(getFreshExposeEntityStore())
    }

    private fun testGetTransaction(entityStore: EntityStore) {
        val entity = ResourceEntity(persona.personaId, URI("http://opencola.org"))
        val epochSecond = Instant.now().epochSecond
        val signedTransaction = entityStore.updateEntities(entity)
        assertNotNull(signedTransaction)
        assert(signedTransaction.transaction.epochSecond >= epochSecond)

        val transaction = entityStore.getTransaction(signedTransaction.transaction.id)
        assertNotNull(transaction)

        val transactionsFromNull =
            entityStore.getSignedTransactions(setOf(persona.personaId), null, TransactionOrder.IdAscending, 100)
        assertNotNull(transactionsFromNull.firstOrNull { it.transaction.id == transaction.transaction.id })
    }

    @Test
    fun testGetTransactionsExposed() {
        testGetTransactions(getFreshExposeEntityStore())
    }

    private fun testGetTransactions(entityStore: EntityStore) {
        val entities = (0 until 3).map { ResourceEntity(persona.personaId, URI("http://test/$it")) }
        val transactions = entities.map { entityStore.updateEntities(it)!! }
        val transactionIds = transactions.map { it.transaction.id }

        val firstTransaction =
            entityStore.getSignedTransactions(setOf(persona.personaId), null, TransactionOrder.IdAscending, 1)
                .firstOrNull()
        assertNotNull(firstTransaction)
        assertEquals(transactions.first(), firstTransaction)

        val firstTransactionAll =
            entityStore.getSignedTransactions(emptySet(), null, TransactionOrder.IdAscending, 1).firstOrNull()
        assertNotNull(firstTransactionAll)
        assertEquals(transactions.first(), firstTransaction)

        val lastTransaction =
            entityStore.getSignedTransactions(setOf(persona.personaId), null, TransactionOrder.IdDescending, 1)
                .firstOrNull()
        assertNotNull(lastTransaction)
        assertEquals(entities.last().entityId, lastTransaction.transaction.transactionEntities.first().entityId)

        val lastTransactionAll =
            entityStore.getSignedTransactions(emptySet(), null, TransactionOrder.IdDescending, 1).firstOrNull()
        assertNotNull(lastTransactionAll)
        assertEquals(transactions.last(), lastTransactionAll)

        val middleTransactionsForward = entityStore.getSignedTransactions(
            setOf(persona.personaId),
            transactionIds[1],
            TransactionOrder.IdAscending,
            10
        )
        assertEquals(transactions.drop(1), middleTransactionsForward)

        val middleTransactionsBackward = entityStore.getSignedTransactions(
            setOf(persona.personaId),
            transactionIds[1],
            TransactionOrder.IdDescending,
            10
        )
        assertEquals(transactions.reversed().drop(1), middleTransactionsBackward)

        val allTransactionsForward =
            entityStore.getSignedTransactions(emptySet(), null, TransactionOrder.IdAscending, 10)
        assertEquals(transactions, allTransactionsForward)

        val allTransactionsBackward =
            entityStore.getSignedTransactions(emptySet(), null, TransactionOrder.IdDescending, 10)
        assertEquals(transactions.reversed(), allTransactionsBackward)

        // TODO - Add tests across AuthorityIds
    }

    // TODO: This only tests the entity store that is used in the test config (i.e. SimpleEntityStore is not tested)
    @Test
    fun testGetFacts() {
        val applications = getApplications(TestApplication.storagePath, TestApplication.config, 6000, 2)
        applications.forEach { it.open(true) }

        try {
            // Create some entities for first authority
            val authority0 = applications[0].getPersonas().first()
            val entityStore0 by applications[0].injector.instance<EntityStore>()
            val entities0 = (0 until 2).map { ResourceEntity(authority0.personaId, URI("http://test/$it")) }
            entityStore0.updateEntities(*entities0.toTypedArray<Entity>())

            // Add some entities from peer store with same entity ids
            val authority1 = applications[1].getPersonas().first()
            val entityStore1 by applications[1].injector.instance<EntityStore>()
            val entities1 = (0 until 2).map { ResourceEntity(authority1.personaId, URI("http://test/$it")) }
            val transaction = entityStore1.updateEntities(*entities1.toTypedArray<Entity>())
                ?: throw RuntimeException("Unable to update entities")
            entityStore0.addSignedTransactions(listOf(transaction))

            val authority0Facts = entityStore0.getFacts(setOf(authority0.personaId), emptySet())
            assert(authority0Facts.isNotEmpty())
            assert(authority0Facts.all { it.authorityId == authority0.personaId })
            assertNotNull(authority0Facts.firstOrNull { it.entityId == entities0[0].entityId })
            assertNotNull(authority0Facts.firstOrNull { it.entityId == entities0[1].entityId })

            val authority1Facts = entityStore0.getFacts(setOf(authority1.personaId), emptySet())
            assert(authority1Facts.isNotEmpty())
            assert(authority1Facts.all { it.authorityId == authority1.personaId })
            assertNotNull(authority1Facts.firstOrNull { it.entityId == entities1[0].entityId })
            assertNotNull(authority1Facts.firstOrNull { it.entityId == entities1[1].entityId })

            val entity0Facts = entityStore0.getFacts(emptySet(), setOf(entities0[0].entityId))
            assert(entity0Facts.isNotEmpty())
            assertTrue { entity0Facts.all { it.entityId == entities0[0].entityId } }
            assertTrue(entity0Facts.any { it.authorityId == authority0.personaId })
            assertTrue(entity0Facts.any { it.authorityId == authority1.personaId })

            val entity1Facts = entityStore0.getFacts(emptySet(), setOf(entities0[1].entityId))
            assert(entity1Facts.isNotEmpty())
            assertTrue { entity1Facts.all { it.entityId == entities0[1].entityId } }
            assertTrue(entity1Facts.any { it.authorityId == authority0.personaId })
            assertTrue(entity1Facts.any { it.authorityId == authority1.personaId })
        } finally {
            println("Closing applications")
            applications.forEach { it.close() }
        }
    }

    @Test
    fun testCommentsWithComputedFactsExposed() {
        testCommentsWithComputedFacts(getFreshExposeEntityStore())
    }

    private fun testCommentsWithComputedFacts(entityStore: EntityStore) {
        val resource = ResourceEntity(persona.personaId, URI("https://opencola"))
        entityStore.updateEntities(resource)

        val comment = CommentEntity(persona.personaId, resource.entityId, "Comment")
        entityStore.updateEntities(comment)

        val comment1 = entityStore.getEntity(persona.personaId, comment.entityId)
        assertNotNull(comment1)
        assertEquals(comment, comment1)

        val resource1 = entityStore.getEntity(persona.personaId, resource.entityId)
        assertNotNull(resource1)
        assertEquals(1, resource1.commentIds.count())
        assertEquals(comment.entityId, resource1.commentIds.single())

        entityStore.deleteEntities(persona.personaId, comment.entityId)
        val resource2 = entityStore.getEntity(persona.personaId, resource.entityId)
        assertNotNull(resource2)
        assertEquals(0, resource2.commentIds.count())
    }

    @Test
    fun testDeleteDependentsExposed() {
        testDeleteDependents(getFreshExposeEntityStore())
    }

    private fun testDeleteDependents(entityStore: EntityStore) {
        val resource = ResourceEntity(persona.personaId, URI("https://opencola"))
        entityStore.updateEntities(resource)

        val comment = CommentEntity(persona.personaId, resource.entityId, "Comment")
        entityStore.updateEntities(comment)

        val comment1 = entityStore.getEntity(persona.personaId, comment.entityId)
        assertNotNull(comment1)
        assertEquals(comment, comment1)

        entityStore.deleteEntities(persona.personaId, resource.entityId)
        assertNull(entityStore.getEntity(persona.personaId, resource.entityId))
        // Deletion of resource should have deleted the dependent comment too
        assertNull(entityStore.getEntity(persona.personaId, comment.entityId))
    }

    @Test
    fun testSetAndNullFactsExposed() {
        testSetAndNullProperties(getFreshExposeEntityStore())
    }

    private fun testSetAndNullProperties(entityStore: EntityStore) {
        val resource = ResourceEntity(
            persona.personaId, URI("http://opencola.io/"), "Name", "Description",
            "Text", URI("http://image.com"), 0.5F, listOf("hi"), true, .7F
        )

        entityStore.updateEntities(resource)
        val resource1 = entityStore.getEntity(persona.personaId, resource.entityId) as? ResourceEntity
        assertNotNull(resource1)
        assertEquals(resource.authorityId, resource1.authorityId)
        assertEquals(resource.entityId, resource1.entityId)
        assertEquals(resource.uri, resource1.uri)
        assertEquals(resource.name, resource1.name)
        assertEquals(resource.description, resource1.description)
        assertEquals(resource.text, resource1.text)
        assertEquals(resource.imageUri, resource1.imageUri)
        assertEquals(resource.trust, resource1.trust)
        assertEquals(resource.tags, resource1.tags)
        assertEquals(resource.like, resource1.like)
        assertEquals(resource.rating, resource1.rating)

        assertFails { resource1.uri = URI("https://test") }
        resource1.name = null
        resource1.description = null
        resource1.text = null
        resource1.imageUri = null
        resource1.trust = null
        resource1.tags = emptyList()
        resource1.like = null
        resource1.rating = null
        entityStore.updateEntities(resource1)

        val resource2 = entityStore.getEntity(persona.personaId, resource.entityId) as? ResourceEntity
        assertNotNull(resource2)
        assertEquals(resource.authorityId, resource2.authorityId)
        assertEquals(resource.entityId, resource2.entityId)
        assertEquals(resource.uri, resource2.uri)
        assertNull(resource2.name)
        assertNull(resource2.description)
        assertNull(resource2.text)
        assertNull(resource2.imageUri)
        assertNull(resource2.trust)
        assertEquals(emptyList(), resource1.tags)
        assertNull(resource2.like)
        assertNull(resource2.rating)

        resource2.like = null
        val transaction = entityStore.updateEntities(resource2)
        // Nothing new set, so transaction shouldn't be created
        assertNull(transaction)
    }

    @Test
    fun testDetectDuplicateFactExposed() {
        testDetectDuplicateFacts(getFreshExposeEntityStore())
    }

    private fun testDetectDuplicateFacts(entityStore: EntityStore) {
        val uri = URI("https://opencola")
        val resource0 = ResourceEntity(persona.personaId, uri)
        entityStore.updateEntities(resource0)

        // Test detection of creation of a duplicate entity
        val resource1 = ResourceEntity(persona.personaId, uri)
        assertFails { entityStore.updateEntities(resource1) }

        // Test detection of duplicate multi value set property
        val resource3 = entityStore.getEntity(persona.personaId, resource0.entityId)!!
        val resource4 = entityStore.getEntity(persona.personaId, resource0.entityId)!!
        resource3.tags = listOf("this", "that")
        entityStore.updateEntities(resource3)
        resource4.tags = listOf("this")
        assertFails { entityStore.updateEntities(resource4) }

        // Test detection of duplicate single value property
        val resource5 = entityStore.getEntity(persona.personaId, resource0.entityId)!!
        val resource6 = entityStore.getEntity(persona.personaId, resource0.entityId)!!
        resource5.like = true
        entityStore.updateEntities(resource5)
        resource6.like = true
        assertFails { entityStore.updateEntities(resource6) }

        // Test detection of nulling out property
        val resource7 = entityStore.getEntity(persona.personaId, resource0.entityId)!!
        val resource8 = entityStore.getEntity(persona.personaId, resource0.entityId)!!
        resource7.like = null
        entityStore.updateEntities(resource7)
        resource8.like = null
        assertFails { entityStore.updateEntities(resource8) }
    }

    @Test
    fun testRejectEmptyValue() {
        val uri = URI("https://opencola")
        val resource0 = ResourceEntity(persona.personaId, uri)
        val factWithEmptyValue =
            Fact(resource0.authorityId, resource0.entityId, CoreAttribute.Name.spec, EmptyValue, Operation.Add, 0, 0)
        val facts = resource0.commitFacts(0, 0).plus(factWithEmptyValue)
        val resource1 = Entity.fromFacts(facts)!!
        assertFails { getFreshExposeEntityStore().updateEntities(resource1) }
    }

    @Test
    fun testTransactionNoCompression() {
        val context = EntityStoreContext()
        val persona = context.addressBook.addPersona("Test")
        val resource = ResourceEntity(persona.personaId, URI("https://opencola"))
        resource.description = "012345" // No redundancy, so compression should not be used
        val signedTransaction = context.entityStore.updateEntities(resource)!!

        assertEquals(CompressionFormat.NONE, signedTransaction.compressedTransaction.format)
    }

    @Test
    fun testTransactionCompression() {
        val context = EntityStoreContext()
        val persona = context.addressBook.addPersona("Test")
        val resource = ResourceEntity(persona.personaId, URI("https://opencola"))
        resource.description = "+".repeat(1000) // Add some redundancy to force compression
        val signedTransaction = context.entityStore.updateEntities(resource)!!

        assertEquals(CompressionFormat.DEFLATE, signedTransaction.compressedTransaction.format)
    }
}