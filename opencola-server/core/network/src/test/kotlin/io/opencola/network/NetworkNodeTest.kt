package io.opencola.network

import io.opencola.application.TestApplication
import io.opencola.model.Id
import io.opencola.network.NetworkNode.Route
import io.opencola.network.message.*
import io.opencola.storage.*
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.filestore.LocalContentBasedFileStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.UUID
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
        val peerKeyPair = context.addPeer("Peer0")
        val envelopeBytes = context.getEncodedEnvelope(peerKeyPair, context.persona.personaId, pingMessage)

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
        val peerKeyPair = context.addPeer("Peer0")
        val envelopeBytes = context.getEncodedEnvelope(peerKeyPair, persona.personaId, pingMessage)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromInactivePeer() {
        val context = NetworkNodeContext()
        val peerKeyPair = context.addPeer("Peer0", false)
        val envelopeBytes = context.getEncodedEnvelope(peerKeyPair, context.persona.personaId, pingMessage)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromValidPeerToWrongPersona() {
        val context = NetworkNodeContext()
        val peerKeyPair = context.addPeer("Peer0")
        val persona1 = context.addressBook.addPersona("Persona1")
        val envelopeBytes = context.getEncodedEnvelope(peerKeyPair, persona1.personaId, pingMessage)
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
            val fileStore = LocalContentBasedFileStore(TestApplication.getTmpDirectory("-filestore"))
            val context = NetworkNodeContext(routes = listOf(getDataRoute(fileStore)))
            val result = CompletableDeferred<SignedMessage>()
            context.provider.onSendMessage = { _, _, message -> result.complete(message) }

            // Create persona and peer
            val peerKeyPair = context.addPeer("Peer0")

            // Add data to file store
            val data = "Hello World ${UUID.randomUUID()}".toByteArray()
            val id = fileStore.write(data)

            // Request data as peer
            val message = GetDataMessage(id).toUnsignedMessage()
            val envelopeBytes = context.getEncodedEnvelope(peerKeyPair, context.persona.personaId, message)
            context.provider.handleMessage(envelopeBytes, false)

            val signedMessage = withTimeout(3000) { result.await() }
            assertEquals(PutDataMessage.messageType, signedMessage.body.type)
            val putDataMessage = PutDataMessage.decodeProto(signedMessage.body.payload)
            assertEquals(id, putDataMessage.id)
            assertContentEquals(data, fileStore.read(id))
        }
    }
}