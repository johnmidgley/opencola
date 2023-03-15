package io.opencola.network

import io.opencola.event.MockEventBus
import io.opencola.model.Id
import io.opencola.network.providers.MockNetworkProvider
import io.opencola.security.generateKeyPair
import io.opencola.security.sign
import io.opencola.storage.MockAddressBook
import io.opencola.storage.PersonaAddressBookEntry
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import java.security.PrivateKey
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull


class NetworkNodeTest {
    private val pingRequest = Request(Request.Method.GET, "/ping")

    private fun getNetworkNode(): NetworkNodeContext {
        val addressBook = MockAddressBook()
        val eventBus = MockEventBus()
        val routes = listOf(Route(
            Request.Method.GET,
            "/ping"
        ) { _, _, _ -> Response(200, "pong") })
        val router = RequestRouter(addressBook, routes)
        val provider = MockNetworkProvider(addressBook, addressBook.keyStore)
        val networkNode = NetworkNode(router, addressBook, eventBus).also { it.addProvider(provider) }

       return NetworkNodeContext(addressBook, provider, networkNode)
    }

    private fun getEncodedEnvelope(fromId: Id, fromPrivateKey: PrivateKey, toId: Id, request: Request): ByteArray {
        val messageBytes = Json.encodeToString(request).toByteArray()
        val message = Message(fromId, messageBytes, sign(fromPrivateKey, messageBytes))
        return MessageEnvelope(toId, message).encode()
    }


    @Test
    fun testSendRequestFromInvalidPersona() {
        val context = getNetworkNode()
        val id = Id.new()
        assertFails { context.networkNode.sendRequest(id, id, pingRequest) }
    }

    @Test
    fun testSendRequestToUnknownPeer() {
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0")
        assertFails { context.networkNode.sendRequest(persona.personaId, Id.new(), pingRequest) }
    }

    @Test
    fun testSendRequestFromInactivePersona() {
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0", false) as PersonaAddressBookEntry
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0")
        context.provider.onSendRequest = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertNull(context.networkNode.sendRequest(persona.personaId, peer.entityId, pingRequest))
    }

    @Test
    fun testSendRequestToInactivePeer() {
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0") as PersonaAddressBookEntry
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", false)
        context.provider.onSendRequest = { _, _, _ -> throw IllegalStateException("Should not be called") }
        assertNull(context.networkNode.sendRequest(persona.personaId, peer.entityId, pingRequest))
    }

    @Test
    fun testSendRequestToActivePeer() {
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0") as PersonaAddressBookEntry
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0")
        val response = Response(200, "pong")
        context.provider.onSendRequest = { _, _, _ -> response }
        assertEquals(response, context.networkNode.sendRequest(persona.personaId, peer.entityId, pingRequest))
    }

    @Test
    fun testSendValidMessage() {
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0") as PersonaAddressBookEntry
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
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0", false) as PersonaAddressBookEntry
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", publicKey = peerKeyPair.public)
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona.personaId, pingRequest)
        assertFails { context.provider.handleMessage(envelopeBytes, false) }
    }

    @Test
    fun testReceiveMessageFromInactivePeer() {
        val context = getNetworkNode()
        val persona = context.addressBook.addPersona("Persona0") as PersonaAddressBookEntry
        val peerKeyPair = generateKeyPair()
        val peer = context.addressBook.addPeer(persona.personaId, "Peer0", false, peerKeyPair.public)
        val envelopeBytes = getEncodedEnvelope(peer.entityId, peerKeyPair.private, persona.personaId, pingRequest)
        assertFails{ context.provider.handleMessage(envelopeBytes, false) }
    }
}