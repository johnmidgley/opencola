package io.opencola.network

import io.opencola.application.TestApplication
import io.opencola.io.waitForStdout
import io.opencola.model.Attributes
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.network.NetworkNode.Route
import io.opencola.network.message.*
import io.opencola.security.MockKeyStore
import io.opencola.security.Signator
import io.opencola.storage.*
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.entitystore.ExposedEntityStoreV2
import io.opencola.storage.db.getSQLiteDB
import io.opencola.util.poll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.lang.IllegalArgumentException
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.test.*


class NetworkNodeTest {
    private val pingMessage = PingMessage()

    @Test
    fun testSendRequestFromInvalidPersona() {
        val context = NetworkNodeContext()
        val id = Id.new()
        assertFails { context.networkNode.sendMessage(id, id, pingMessage) }
    }

    @Test
    fun testSendRequestToUnknownPeer() {
        val context = NetworkNodeContext()
        assertFails { context.networkNode.sendMessage(context.persona.personaId, Id.new(), pingMessage) }
    }

    @Test
    fun testSendRequestFromInactivePersona() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("InactivePersona", false)
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0")
        context.provider.onSendMessage = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertFails { context.networkNode.sendMessage(persona.personaId, peer.entityId, pingMessage) }
    }

    @Test
    fun testSendRequestToInactivePeer() {
        val context = NetworkNodeContext()
        val peer = context.addressBook.addPeer(context.persona.personaId, "Peer0", false)
        context.provider.onSendMessage = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertFails { context.networkNode.sendMessage(context.persona.personaId, peer.entityId, pingMessage) }
    }

    @Test
    fun testSendRequestToActivePeer() {
        val context = NetworkNodeContext()
        val peer = context.addressBook.addPeer(context.persona.personaId, "Peer0")
        var response = false
        context.provider.onSendMessage = { _, _, _ -> response = true }
        context.networkNode.sendMessage(context.persona.personaId, peer.entityId, pingMessage)
        assertTrue(response)
    }

    @Test
    fun testSendValidMessage() {
        val context = NetworkNodeContext()
        val peer0 = context.addPeer("Peer0")

        runBlocking {
            val result = CompletableDeferred<Unit>()
            context.setRoute(Route(PingMessage::class) { _, _, _ -> result.complete(Unit); emptyList() })
            context.provider.handleMessage(peer0.addressBookEntry.entityId, context.persona.personaId, pingMessage)
            withTimeout(3000) { result.await() }
        }
    }

    @Test
    fun testReceiveMessageToInactivePersona() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0", false)
        val peer0 = context.addPeer("Peer0")
        // TODO: Replace assertFails with more specific assertFailsWith<Exception> calls
        assertFailsWith<IllegalArgumentException> {
            context.provider.handleMessage(peer0.addressBookEntry.entityId, persona.personaId, pingMessage)
        }
    }

    @Test
    fun testReceiveMessageFromInactivePeer() {
        val context = NetworkNodeContext()
        val peer0 = context.addPeer("Peer0", false)
        assertFailsWith<IllegalArgumentException> {
            context.provider.handleMessage(peer0.addressBookEntry.entityId, context.persona.personaId, pingMessage)
        }
    }

    @Test
    fun testReceiveMessageFromValidPeerToWrongPersona() {
        val context = NetworkNodeContext()
        val peer0 = context.addPeer("Peer0")
        val persona1 = context.addressBook.addPersona("Persona1")
        assertFailsWith<IllegalArgumentException> {
            context.provider.handleMessage(peer0.addressBookEntry.entityId, persona1.entityId, pingMessage)
        }
    }

    private fun validateRecipient(validRecipients: Set<AddressBookEntry>): (PersonaAddressBookEntry, AddressBookEntry, Message) -> Unit {
        return { from: PersonaAddressBookEntry, to: AddressBookEntry, _: Message ->
            if (!validRecipients.contains(to))
                throw IllegalStateException("Invalid call to SendMessage: from: $from to: $to")
        }
    }

    @Test
    fun testBroadcast() {
        val context = NetworkNodeContext()
        val persona0 = context.addressBook.addPersona("Persona0")
        val persona0Peer0 = context.addressBook.addPeer(persona0.personaId, "persona0Peer0")
        val persona0Peer1 = context.addressBook.addPeer(persona0.personaId, "persona0Peer1")

        val persona1 = context.addressBook.addPersona("Persona1")
        val persona1Peer0 = context.addressBook.addPeer(persona1.personaId, "persona1Peer0")
        val persona1Peer1 = context.addressBook.addPeer(persona1.personaId, "persona1Peer1")

        validateRecipient(setOf(persona0Peer0, persona0Peer1))
        context.provider.onSendMessage = validateRecipient(setOf(persona0Peer0, persona0Peer1))
        context.networkNode.broadcastMessage(persona0, pingMessage)

        context.provider.onSendMessage = validateRecipient(setOf(persona1Peer0, persona1Peer1))
        context.networkNode.broadcastMessage(persona1, pingMessage)
    }

    @Test
    fun testGetData() {
        runBlocking {
            val context = NetworkNodeContext()
            val result = CompletableDeferred<Message>()
            context.provider.onSendMessage = { _, _, message -> result.complete(message) }

            // Create persona and peer
            val peer0 = context.addPeer("Peer0")

            // Add data to file store
            val data = "Hello World ${UUID.randomUUID()}".toByteArray()
            val dataId = context.contentBasedFileStore.write(data)

            // Request data without data entity - should fail
            waitForStdout("for unknown data id") {
                context.provider.handleMessage(
                    peer0.addressBookEntry.entityId,
                    context.persona.personaId,
                    GetDataMessage(dataId)
                )
            }

            // Add data reference to entity store
            context.entityStore.updateEntities(DataEntity(context.persona.personaId, dataId, ""))

            // Try to get data - should succeed now
            context.provider.handleMessage(peer0.addressBookEntry.entityId, context.persona.personaId, GetDataMessage(dataId))
            val message = withTimeout(3000) { result.await() }
            val putDataMessage = message as? PutDataMessage ?: fail("Expected PutDataMessage")
            assertEquals(dataId, putDataMessage.dataId)
            assertContentEquals(data, context.contentBasedFileStore.read(dataId))
        }
    }

    @Test
    fun testGetTransactionBatching() {
        val context = NetworkNodeContext()
        val peer0 = context.addPeer("Peer0")
        val results = LinkedBlockingDeque<PutTransactionMessage>()
        context.provider.onSendMessage = { _, _, message -> results.add(message as PutTransactionMessage) }

        val transactionIds = (0 until 10)
            .map { getTestEntity(context.persona, it) }
            .mapNotNull { context.entityStore.updateEntities(it)?.transaction?.id }

        assert(transactionIds.count() == 10)
        var transactionNum = 0

        val message0 = GetTransactionsMessage(null, null, 4)
        context.handleMessage(peer0, context.persona.entityId, message0)

        repeat(3) {
            poll(results)?.let {
                assertEquals(transactionIds[transactionNum++], it.getSignedTransaction().transaction.id)
                assertNull(it.lastTransactionId)
            } ?: fail("Expected message")
        }

        val putTransactionMessage = poll(results) ?: fail("Expected message")
        val signedTransaction = putTransactionMessage.getSignedTransaction()
        assertEquals(transactionIds[transactionNum++], signedTransaction.transaction.id)
        assertNotNull(putTransactionMessage.lastTransactionId)

        // Make sure no pending messages
        assertNull(results.poll(500, TimeUnit.MILLISECONDS))

        val message1 = GetTransactionsMessage(null, signedTransaction.transaction.id, 10)
        context.handleMessage(peer0, context.persona.entityId, message1)

        repeat(6) {
            poll(results)?.let {
                assertEquals(transactionIds[transactionNum++], it.getSignedTransaction().transaction.id)
                assertNull(it.lastTransactionId)
            } ?: fail("Expected message")
        }

        // Make sure no pending messages
        assertNull(results.poll(500, TimeUnit.MILLISECONDS))
    }

    private fun getExposedEntityStoreV2NetworkNodeContext(): NetworkNodeContext {
        val keyStore = MockKeyStore()
        val signator = Signator(keyStore)
        val addressBook = MockAddressBook()
        val entityStore = ExposedEntityStoreV2(
            "entity-store",
            TestApplication.getTmpDirectory("entity-store"),
            ::getSQLiteDB,
            Attributes.get(),
            signator,
            addressBook
        )

        return NetworkNodeContext(
            keyStore = keyStore,
            signator = signator,
            addressBook = addressBook,
            entityStore = entityStore
        )
    }

    @Test
    fun testForwardTransactionCaching() {
        val peerEntityStoreContext = EntityStoreContext()
        val peerPersona = peerEntityStoreContext.addressBook.addPersona("PeerPersona")

        val signedTransactions = (0 until 10)
            .map { getTestEntity(peerPersona, it) }
            .mapNotNull { peerEntityStoreContext.entityStore.updateEntities(it) }

        val networkNodeContext = getExposedEntityStoreV2NetworkNodeContext()
        val peer = networkNodeContext.addPeer("Peer0", true, peerPersona.keyPair)

        // Check that add all but last transaction in reverse order doesn't add any transactions to DB
        (9 downTo 1)
            .forEach {
                val tx = signedTransactions[it]
                val message = PutTransactionMessage(tx)
                networkNodeContext.handleMessage(peer, networkNodeContext.persona.entityId, message)
                assertEquals(0, networkNodeContext.entityStore.getAllSignedTransactions().count())
            }

        // Add last transaction (first in id order) and check that all other transactions are added
        val tx0 = signedTransactions[0]
        val message = PutTransactionMessage(tx0)
        networkNodeContext.handleMessage(peer, networkNodeContext.persona.entityId, message)
        val allSignedTransactions = networkNodeContext.entityStore.getAllSignedTransactions().toList()
        assertEquals(10, allSignedTransactions.count())
        assertEquals(signedTransactions.map { it.transaction.id }, allSignedTransactions.map { it.transaction.id })
    }
}