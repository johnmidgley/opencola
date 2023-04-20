package io.opencola.network

import io.opencola.application.TestApplication
import io.opencola.model.Id
import io.opencola.security.generateKeyPair
import io.opencola.storage.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.util.UUID
import kotlin.test.*


class NetworkNodeTest {
    private val pingRequest = Request(Request.Method.GET, "/ping")

    @Test
    fun testSendRequestFromInvalidPersona() {
        val context = NetworkNodeContext()
        val id = Id.new()
        assertFails { context.networkNode.sendRequest(id, id, pingRequest) }
    }

    @Test
    fun testSendRequestToUnknownPeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        assertFails { context.networkNode.sendRequest(persona.personaId, Id.new(), pingRequest) }
    }

    @Test
    fun testSendRequestFromInactivePersona() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0", false)
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0")
        context.provider.onSendRequest = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertNull(context.networkNode.sendRequest(persona.personaId, peer.entityId, pingRequest))
    }

    @Test
    fun testSendRequestToInactivePeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", false)
        context.provider.onSendRequest = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertNull(context.networkNode.sendRequest(persona.personaId, peer.entityId, pingRequest))
    }

    @Test
    fun testSendRequestToActivePeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0")
        val response = Response(200, "pong")
        context.provider.onSendRequest = { _, _, _ -> response }
        assertEquals(response, context.networkNode.sendRequest(persona.personaId, peer.entityId, pingRequest))
    }

    @Test
    fun testSendValidMessage() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona.personaId, pingRequest)
        val responseBytes = context.provider.handleMessage(envelopeBytes, false)
        val responseEnvelope = MessageEnvelope.decode(responseBytes)
        val response = Json.decodeFromString<Response>(String(responseEnvelope.message.body))
        assertEquals(200, response.status)
        assertEquals("pong", response.message)
    }

    @Test
    fun testReceiveMessageToInactivePersona() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0", false)
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona.personaId, pingRequest)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromInactivePeer() {
        val context = NetworkNodeContext()
        val persona = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", false, peerKeyPair.public)
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona.personaId, pingRequest)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromValidPeerToWrongPersona() {
        val context = NetworkNodeContext()
        val persona0 = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona0.personaId, "Peer0", true, peerKeyPair.public)
        val persona1 = context.addressBook.addPersona("Persona1")
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona1.personaId, pingRequest)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    private fun validateRecipient(validRecipients: Set<AddressBookEntry>) : (PersonaAddressBookEntry, AddressBookEntry, Request) -> Response? {
        return { from: PersonaAddressBookEntry, to: AddressBookEntry, _: Request ->
            if (!validRecipients.contains(to))
                throw IllegalStateException("Invalid call to SendMessage: from: $from to: $to")
            null
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

        context.provider.onSendRequest = validateRecipient(setOf(persona0Peer0, persona0Peer1))
        context.networkNode.broadcastRequest(persona0, pingRequest)

        context.provider.onSendRequest = validateRecipient(setOf(persona1Peer0, persona1Peer1))
        context.networkNode.broadcastRequest(persona1, pingRequest)
    }

    @Test
    fun testGetData() {
        val fileStore = LocalFileStore(TestApplication.getTmpDirectory("-filestore"))
        val context = NetworkNodeContext(routes = listOf(dataRoute(fileStore)))

        // Create persona and peer
        val persona = context.addressBook.addPersona("Persona0")
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)

        // Add data to file store
        val data = "Hello World ${UUID.randomUUID()}".toByteArray()
        val id = fileStore.write(data)

        // Request data as peer
        val dataRequest = Request(Request.Method.GET, "/data",  parameters =  mapOf("id" to id.toString()))
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona.personaId, dataRequest)
        val responseBytes = context.provider.handleMessage(envelopeBytes, false)
        val responseEnvelope = MessageEnvelope.decode(responseBytes)
        val response = Json.decodeFromString<Response>(String(responseEnvelope.message.body)).body

        assertNotNull(response)
        assertContentEquals(data, response)
    }
}