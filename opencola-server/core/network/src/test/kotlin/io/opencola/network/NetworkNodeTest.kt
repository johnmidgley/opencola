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
import io.opencola.storage.entitystore.EntityStoreConfig
import io.opencola.storage.entitystore.ExposedEntityStoreV2
import io.opencola.storage.entitystore.getSQLiteDB
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.security.PublicKey
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
        val envelopeBytes = context.getEncodedEnvelope(peer0.keyPair, context.persona.personaId, pingMessage)

        runBlocking {
            val result = CompletableDeferred<Unit>()
            context.setRoute(Route(PingMessage.messageType) { _, _, _ -> result.complete(Unit); emptyList() })
            context.provider.handleMessage(envelopeBytes, false)
            withTimeout(3000) { result.await() }
        }
    }

    @Test
    fun testReceiveMessageToInactivePersona() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0", false)
        val peer0 = context.addPeer("Peer0")
        val envelopeBytes = context.getEncodedEnvelope(peer0.keyPair, persona.personaId, pingMessage)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromInactivePeer() {
        val context = NetworkNodeContext()
        val peer0 = context.addPeer("Peer0", false)
        val envelopeBytes = context.getEncodedEnvelope(peer0.keyPair, context.persona.personaId, pingMessage)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromValidPeerToWrongPersona() {
        val context = NetworkNodeContext()
        val peer0 = context.addPeer("Peer0")
        val persona1 = context.addressBook.addPersona("Persona1")
        val envelopeBytes = context.getEncodedEnvelope(peer0.keyPair, persona1.personaId, pingMessage)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    private fun validateRecipient(validRecipients: Set<AddressBookEntry>): (PersonaAddressBookEntry, AddressBookEntry, SignedMessage) -> Unit {
        return { from: PersonaAddressBookEntry, to: AddressBookEntry, _: SignedMessage ->
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
            val result = CompletableDeferred<SignedMessage>()
            context.provider.onSendMessage = { _, _, message -> result.complete(message) }

            // Create persona and peer
            val peer0 = context.addPeer("Peer0")

            // Add data to file store
            val data = "Hello World ${UUID.randomUUID()}".toByteArray()
            val dataId = context.contentBasedFileStore.write(data)

            // Request data without data entity - should fail
            waitForStdout("for unknown data id") {
                val message = GetDataMessage(dataId).toUnsignedMessage()
                val envelopeBytes = context.getEncodedEnvelope(peer0.keyPair, context.persona.personaId, message)
                context.provider.handleMessage(envelopeBytes, false)
            }

            // Add data reference to entity store
            context.entityStore.updateEntities(DataEntity(context.persona.personaId, dataId, ""))

            // Try to get data - should succeed now
            val message = GetDataMessage(dataId).toUnsignedMessage()
            val envelopeBytes = context.getEncodedEnvelope(peer0.keyPair, context.persona.personaId, message)
            context.provider.handleMessage(envelopeBytes, false)

            val signedMessage = withTimeout(3000) { result.await() }
            assertEquals(PutDataMessage.messageType, signedMessage.body.type)
            val putDataMessage = PutDataMessage.decodeProto(signedMessage.body.payload)
            assertEquals(dataId, putDataMessage.id)
            assertContentEquals(data, context.contentBasedFileStore.read(dataId))
        }
    }

    fun decodePutTransactionsMessage(publicKey: PublicKey, signedMessage: SignedMessage?): PutTransactionMessage {
        assertNotNull(signedMessage)
        assertEquals(PutTransactionMessage.messageType, signedMessage.body.type)
        assert(signedMessage.hasValidSignature(publicKey))
        return PutTransactionMessage.decodeProto(signedMessage.body.payload).also {
            assert(it.getSignedTransaction().hasValidSignature(publicKey))
        }
    }

    fun <T> poll(q: LinkedBlockingDeque<T>): T? {
        return q.poll(1000, TimeUnit.MILLISECONDS)
    }

    @Test
    fun testGetTransactionBatching() {
        val context = NetworkNodeContext()
        val persona = context.persona
        val peer0 = context.addPeer("Peer0")
        val results = LinkedBlockingDeque<SignedMessage>()
        context.provider.onSendMessage = { _, _, message -> results.add(message) }

        val transactionIds = (0 until 10)
            .map { getTestEntity(context.persona, it) }
            .mapNotNull { context.entityStore.updateEntities(it)?.transaction?.id }

        assert(transactionIds.count() == 10)
        var transactionNum = 0

        val message0 = GetTransactionsMessage(null, 4)
        context.handleMessage(peer0, context.persona.entityId, message0.toUnsignedMessage())

        repeat(3) {
            decodePutTransactionsMessage(persona.publicKey, poll(results)).let {
                assertEquals(transactionIds[transactionNum++], it.getSignedTransaction().transaction.id)
                assertNull(it.lastTransactionId)
            }
        }

        val putTransactionMessage = decodePutTransactionsMessage(persona.publicKey, poll(results))
        val signedTransaction = putTransactionMessage.getSignedTransaction()
        assertEquals(transactionIds[transactionNum++], signedTransaction.transaction.id)
        assertNotNull(putTransactionMessage.lastTransactionId)

        // Make sure no pending messages
        assertNull(results.poll(500, TimeUnit.MILLISECONDS))

        val message1 = GetTransactionsMessage(signedTransaction.transaction.id, 10)
        context.handleMessage(peer0, context.persona.entityId, message1.toUnsignedMessage())

        repeat(6) {
            decodePutTransactionsMessage(persona.publicKey, poll(results)).let {
                assertEquals(transactionIds[transactionNum++], it.getSignedTransaction().transaction.id)
                assertNull(it.lastTransactionId)
            }
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
            EntityStoreConfig(),
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
                val message = PutTransactionMessage(signedTransactions[it].encodeProto()).toUnsignedMessage()
                networkNodeContext.handleMessage(peer, networkNodeContext.persona.entityId, message)
                assertEquals(0, networkNodeContext.entityStore.getAllSignedTransactions().count())
            }

        // Add last transaction (first in id order) and check that all other transactions are added
        val message = PutTransactionMessage(signedTransactions[0].encodeProto()).toUnsignedMessage()
        networkNodeContext.handleMessage(peer, networkNodeContext.persona.entityId, message)
        val allSignedTransactions = networkNodeContext.entityStore.getAllSignedTransactions().toList()
        assertEquals(10, allSignedTransactions.count())
        assertEquals(signedTransactions.map { it.transaction.id }, allSignedTransactions.map { it.transaction.id })
    }
}