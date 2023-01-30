package opencola.core.network.providers.relay

import io.opencola.application.Application
import io.opencola.model.Authority
import io.opencola.model.Id
import io.opencola.model.Persona
import io.opencola.network.NetworkNode
import io.opencola.network.Request
import io.opencola.network.providers.relay.OCRelayNetworkProvider
import io.opencola.security.generateKeyPair
import io.opencola.storage.AddressBook
import io.opencola.relay.client.WebSocketClient
import io.opencola.relay.client.defaultOCRPort
import io.opencola.relay.server.startWebServer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import opencola.server.PeerNetworkTest
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OCRelayNetworkProviderTest : PeerNetworkTest() {
    private val ocRelayUri = URI("ocr://0.0.0.0")

    private fun setPeerAddressToRelay(peerId: Id, addressBook: AddressBook) : Authority {
        val peer = addressBook.getPeer(peerId).firstOrNull() ?:
            throw IllegalStateException("Authority not found")
        peer.uri = ocRelayUri
        return addressBook.updateAuthority(peer)
    }

    @Test
    fun testOCRelayNetworkProvider() {
        println("Getting applications")
        val (app0, app1) = getApplications(2)

        val app0Persona = app0.inject<AddressBook>().getAuthorities().filterIsInstance<Persona>().single()
        val app1Persona = app1.inject<AddressBook>().getAuthorities().filterIsInstance<Persona>().single()

        println("Setting Relay Addresses")
        val peer1 = setPeerAddressToRelay(app0Persona.entityId, app0.inject())
        val peer0 = setPeerAddressToRelay(app1Persona.entityId, app1.inject())

        println("Starting relay server")
        val webServer = startWebServer(defaultOCRPort)
        println("Starting network node0")
        val networkNode0 = app0.inject<NetworkNode>().also { it.start(true) }
        println("Starting network node1")
        val networkNode1 = app1.inject<NetworkNode>().also { it.start(true) }

        try {
            println("Testing ping")
            run {
                val response =
                    networkNode0.sendRequest(app0Persona.entityId, peer1.entityId, Request(Request.Method.GET, "/ping"))
                assertNotNull(response)
                assertEquals("pong", response.message)
            }

            println("Testing bad 'from' id")
            run {
                val response =
                    networkNode0.sendRequest(app0Persona.entityId, Id.new(), Request(Request.Method.GET, "/ping"))
                assertNull(response)
            }

            println("Testing wrong public key on recipient side")
            run {
                val addressBook1 = app1.inject<AddressBook>()
                val peer = addressBook1.getAuthority(app1Persona.entityId, peer0.entityId)!!
                peer.publicKey = generateKeyPair().public
                addressBook1.updateAuthority(peer)
                val response =
                    networkNode0.sendRequest(app0Persona.entityId, peer1.entityId, Request(Request.Method.GET, "/ping"))
                assertNull(response)
            }

            // TODO: Test bad signature
            // TODO: Configure request timeout so that tests can run more quickly
        } finally {
            println("Stopping network node0")
            networkNode0.stop()
            println("Stopping network node1")
            networkNode1.stop()
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

        setPeerAddressToRelay(app0.getPersonas().single().entityId, app0.inject() )
        setPeerAddressToRelay(app1.getPersonas().single().entityId, app1.inject() )

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
            setPeerAddressToRelay(app1.getPersonas().first().entityId, app0.inject())
            setPeerAddressToRelay(app0.getPersonas().first().entityId, app1.inject())

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
                        "bad message".toByteArray(),
                        false
                    )

                    val result = relayClient.sendMessage(app1.getPersonas().single().publicKey!!, envelope)
                    assertNull(result)
                }
            } finally {
                println("Closing relay client")
                relayClient.close()
                println("Stopping network node0")
                networkNode0.stop()
                println("Stopping relay server")
                webServer.stop(200, 200)
            }
        }
    }
}