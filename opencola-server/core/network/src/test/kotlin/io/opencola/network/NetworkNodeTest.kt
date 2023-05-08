package io.opencola.network

import io.opencola.application.TestApplication
import io.opencola.model.Id
import io.opencola.network.message.GetDataMessage
import io.opencola.network.message.PingMessage
import io.opencola.network.message.SignedMessage
import io.opencola.security.generateKeyPair
import io.opencola.storage.*
import org.junit.Test
import java.util.UUID
import kotlin.test.*


class NetworkNodeTest {
    val pingMessage = PingMessage()

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
        assertNull(context.networkNode.sendMessage(persona.personaId, peer.entityId, pingMessage))
    }

    @Test
    fun testSendRequestToInactivePeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", false)
        context.provider.onSendMessage = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertNull(context.networkNode.sendMessage(persona.personaId, peer.entityId, pingMessage))
    }

    @Test
    fun testSendRequestToActivePeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0")
        var response = false
        context.provider.onSendMessage = { _, _, _ -> response = true }
        context.networkNode.sendMessage(persona.personaId, peer.entityId, pingMessage)
        assertTrue(response)
    }

    @Test
    fun testSendValidMessage() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)
        val envelopeBytes = context.provider.getEncodedEnvelope(peer.entityId, persona.personaId, pingMessage, false)
        val responseBytes = context.provider.handleMessage(envelopeBytes, false)
        TODO("Check response message, pong, is sent")
    }

    @Test
    fun testReceiveMessageToInactivePersona() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0", false)
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)
        val envelopeBytes = context.provider.getEncodedEnvelope(peer.entityId, persona.personaId, pingMessage, false)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromInactivePeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", false, peerKeyPair.public)
        val envelopeBytes = context.provider.getEncodedEnvelope(peer.entityId, persona.personaId, pingMessage, false)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromValidPeerToWrongPersona() {
        val context = NetworkNodeContext()
        val persona0 = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona0.personaId, "Peer0", true, peerKeyPair.public)
        val persona1 = context.addressBook.addPersona("Persona1")
        val envelopeBytes = context.provider.getEncodedEnvelope(peer.entityId, persona1.personaId, pingMessage, false)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    private fun validateRecipient(validRecipients: Set<AddressBookEntry>) : (PersonaAddressBookEntry, AddressBookEntry, SignedMessage) -> Unit {
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

        val thing = validateRecipient(setOf(persona0Peer0, persona0Peer1))
        context.provider.onSendMessage = validateRecipient(setOf(persona0Peer0, persona0Peer1))
        context.networkNode.broadcastMessage(persona0, pingMessage)

        context.provider.onSendMessage = validateRecipient(setOf(persona1Peer0, persona1Peer1))
        context.networkNode.broadcastMessage(persona1, pingMessage)
    }

    @Test
    fun testGetData() {
        val fileStore = LocalFileStore(TestApplication.getTmpDirectory("-filestore"))
        val context = NetworkNodeContext(routes = listOf(getDataRoute(fileStore)))

        // Create persona and peer
        val persona = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)

        // Add data to file store
        val data = "Hello World ${UUID.randomUUID()}".toByteArray()
        val id = fileStore.write(data)

        // Request data as peer

        val message = GetDataMessage(setOf(id)).toMessage()
        val envelopeBytes = context.provider.getEncodedEnvelope(peer.entityId, persona.personaId, message, false)
        TODO("Check that correct response message is sent")
    }
}