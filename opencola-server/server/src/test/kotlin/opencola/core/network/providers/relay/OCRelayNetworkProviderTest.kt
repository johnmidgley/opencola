package opencola.core.network.providers.relay

import io.opencola.application.Application
import io.opencola.model.Id
import io.opencola.network.NetworkNode
import io.opencola.network.emptyByteArray
import io.opencola.network.message.UnsignedMessage
import io.opencola.network.message.PingMessage
import io.opencola.network.providers.relay.OCRelayNetworkProvider
import io.opencola.storage.AddressBook
import io.opencola.relay.client.WebSocketClient
import io.opencola.relay.client.defaultOCRPort
import io.opencola.relay.server.startWebServer
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import opencola.server.PeerNetworkTest
import org.junit.Test
import java.net.URI
import kotlin.test.assertFails
import kotlin.test.assertNull

class OCRelayNetworkProviderTest : PeerNetworkTest() {
    private val ocRelayUri = URI("ocr://0.0.0.0")
    val pingMessage = PingMessage().toMessage()


    private fun setPeerAddressToRelay(addressBook: AddressBook, peerId: Id) : AddressBookEntry {
        val peer = addressBook.getEntries().firstOrNull { it.entityId == peerId } ?:
            throw IllegalStateException("Authority not found")
        val updatedEntry = AddressBookEntry(peer.personaId, peer.entityId, peer.name, peer.publicKey, ocRelayUri, null, peer.isActive)
        return addressBook.updateEntry(updatedEntry)
    }

    @Test
    fun testOCRelayNetworkProvider() {
        println("Getting applications")
        val (app0, app1) = getApplications(2)

        val app0Persona = app0.inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>().single()
        val app0AddressBook = app0.inject<AddressBook>()
        val app1Persona = app1.inject<AddressBook>().getEntries().filterIsInstance<PersonaAddressBookEntry>().single()
        val app1AddressBook = app1.inject<AddressBook>()

        println("app0Persona=$app0Persona")
        println("app1Persona=$app1Persona")

        println("Setting Relay Addresses")

        setPeerAddressToRelay(app0.inject(), app1Persona.entityId)
        setPeerAddressToRelay(app1.inject(), app0Persona.entityId)

        println("app0AddressBook=\n$app0AddressBook")
        println("app1AddressBook=\n$app1AddressBook")

        println("Starting relay server")
        val webServer = startWebServer(defaultOCRPort)
        println("Starting network node0")
        val networkNode0 = app0.inject<NetworkNode>().also { it.start(true) }
        println("Starting network node1")
        app1.inject<NetworkNode>().also { it.start(true) }

        TODO("Fix response checks")

        try {
            println("Testing ping: from=${app0Persona.entityId} to=${app1Persona.entityId}")
            run {
                val response =
                    networkNode0.sendMessage(app0Persona.entityId, app1Persona.entityId, pingMessage)

            }

            println("Testing bad 'from' id")
            run {
                assertFails {
                    networkNode0.sendMessage(Id.new(), app1Persona.entityId, pingMessage)
                }
            }

            println("Testing bad 'to' id")
            run {
                assertFails {
                    networkNode0.sendMessage(app0Persona.entityId, Id.new(), pingMessage)
                }
            }

//  TODO: Not allowed right now.
//            println("Testing wrong public key on recipient side")
//            run {
//                val addressBook1 = app1.inject<AddressBook>()
//                val peer = addressBook1.getEntry(app1Persona.entityId, app0Persona.entityId)!!
//                val updatedPeer = AddressBookEntry(peer.personaId, peer.entityId, peer.name, generateKeyPair().public, peer.address, null, peer.isActive)
//                addressBook1.updateEntry(updatedPeer)
//                val response =
//                    networkNode0.sendRequest(app0Persona.entityId, app1Persona.entityId, Request(Request.Method.GET, "/ping"))
//                assertNull(response)
//            }

            // TODO: Test bad signature
            // TODO: Configure request timeout so that tests can run more quickly
        } finally {
            println("Stopping app0")
            app0.close()
            println("Stopping app1")
            app1.close()
            println("Stopping relay server")
            webServer.stop(200, 200)
        }
    }

    @Test
    fun testRelayConnectAndReplicate() {
        val application0 = getApplicationNode().also { it.start() }
        val application1 = getApplicationNode().also { it.start() }
        val app0 = application0.application
        val app1 = application1.application

        val relayServer = startWebServer(defaultOCRPort)

        setPeerAddressToRelay(app0.inject(), app0.getPersonas().single().entityId)
        setPeerAddressToRelay(app1.inject(), app1.getPersonas().single().entityId)

        try {
            testConnectAndReplicate(application0, application1)
        } finally {
            application0.stop()
            application1.stop()
            relayServer.stop(200,200)
        }
    }



    @Test
    fun testIgnoreBadSerialization() {
        runBlocking {
            println("Getting applications")
            val (app0, app1) = getApplications(2)

            println("Setting Relay Addresses")
            setPeerAddressToRelay(app0.inject(), app1.getPersonas().first().entityId)
            setPeerAddressToRelay(app1.inject(), app0.getPersonas().first().entityId)

            println("Starting relay server")
            val webServer = startWebServer(defaultOCRPort)

            println("Starting network node0")
            val networkNode0 = app0.inject<NetworkNode>().also { it.start(true) }

            println("Starting network node1")
            app1.inject<NetworkNode>().also { it.start(true) }

            println("Creating relay client")
            val app0KeyPair = Application.getOrCreateRootKeyPair(app0.storagePath, "password").single()
            networkNode0.stop()
            val relayClient = WebSocketClient(URI("ocr://0.0.0.0"), app0KeyPair, "client", 5000)
            launch { relayClient.open { _, _ -> "nothing".toByteArray() } }
            relayClient.waitUntilOpen()

            try {
                println("Testing bad message")
                run {
                    val relayProvider = app0.inject<OCRelayNetworkProvider>()
                    val envelope = relayProvider.getEncodedEnvelope(
                        app0.getPersonas().single().entityId,
                        app1.getPersonas().single().entityId,
                        UnsignedMessage("bad message", emptyByteArray),
                        false
                    )

                    val result = relayClient.sendMessage(app1.getPersonas().single().publicKey, envelope)
                    assertNull(result)
                }
            } finally {
                app0.close()
                app1.close()
                println("Closing relay client")
                relayClient.close()
                println("Stopping relay server")
                webServer.stop(200, 200)
            }
        }
    }
}