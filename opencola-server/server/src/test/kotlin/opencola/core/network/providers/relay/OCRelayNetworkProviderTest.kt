package opencola.core.network.providers.relay

import io.opencola.application.Application
import io.opencola.io.StdoutMonitor
import io.opencola.model.Id
import io.opencola.network.NetworkNode
import io.opencola.network.message.PingMessage
import io.opencola.network.message.PongMessage
import io.opencola.network.pongRoute
import io.opencola.network.protobuf.Network as Proto
import io.opencola.storage.addressbook.AddressBook
import io.opencola.relay.client.v2.WebSocketClient
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.server.startWebServer
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.util.toProto
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import opencola.server.getApplicationNode
import opencola.server.getApplications
import opencola.server.testConnectAndReplicate
import org.junit.Test
import java.net.URI
import java.util.*
import kotlin.test.assertFails

class OCRelayNetworkProviderTest {
    private val ocRelayUri = URI("ocr://0.0.0.0")
    private val pingMessage = PingMessage()


    private fun setPeerAddressToRelay(addressBook: AddressBook, peerId: Id): AddressBookEntry {
        val peer = addressBook.getEntries().firstOrNull { it.entityId == peerId }
            ?: throw IllegalStateException("Authority not found")
        val updatedEntry =
            AddressBookEntry(peer.personaId, peer.entityId, peer.name, peer.publicKey, ocRelayUri, null, peer.isActive)
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
        app0.open(true)
        val networkNode0 = app0.inject<NetworkNode>()
        println("Starting network node1")
        app1.open(true)

        try {
            runBlocking {
                // Trap pong messages so that we can tell when the ping was successful
                val results = Channel<Unit>()
                networkNode0.routes = networkNode0.routes.map {
                    if (it.messageClass == PongMessage::class)
                        pongRoute { _, _, _ -> launch { results.send(Unit) }; emptyList() }
                    else
                        it
                }

                withTimeout(3000) {
                    println("Testing ping: from=${app0Persona.entityId} to=${app1Persona.entityId}")
                    networkNode0.sendMessage(app0Persona.entityId, app1Persona.entityId, pingMessage)
                    results.receive()
                }

                println("Testing bad 'from' id")
                assertFails { networkNode0.sendMessage(Id.new(), app1Persona.entityId, pingMessage) }

                println("Testing bad 'to' id")
                assertFails { networkNode0.sendMessage(app0Persona.entityId, Id.new(), pingMessage) }

                // TODO: Test wrong public key on recipient side
                // TODO: Test bad signature
                // TODO: Configure request timeout so that tests can run more quickly
            }
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
        val application0 = getApplicationNode()
        val application1 = getApplicationNode()
        val app0 = application0.application
        val app1 = application1.application

        val relayServer = startWebServer(defaultOCRPort)

        setPeerAddressToRelay(app0.inject(), app0.getPersonas().single().entityId)
        setPeerAddressToRelay(app1.inject(), app1.getPersonas().single().entityId)

        try {
            testConnectAndReplicate(application0, application1)
        } finally {
            relayServer.stop(200, 200)
        }
    }

    fun getBadPutDataMessageBytes(from: Id): ByteArray {
        return Proto.Message.newBuilder()
            .setId(UUID.randomUUID().toProto())
            .setFrom(from.toProto())
            .setPutData(Proto.PutDataMessage.newBuilder().build())
            .build()
            .toByteArray()
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
            app0.open(true)

            println("Starting network node1")
            app1.open()

            println("Creating relay client")
            val app0KeyPair = Application.getOrCreateRootKeyPair(app0.storagePath, "password").single()
            val relayClient = WebSocketClient(URI("ocr://0.0.0.0"), app0KeyPair, "client", 5000)
            launch { relayClient.open { _, _ -> "nothing".toByteArray() } }
            relayClient.waitUntilOpen()

            try {
                println("Testing bad message")
                run {
                    StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                        relayClient.sendMessage(
                            app1.getPersonas().single().publicKey,
                            MessageStorageKey.none,
                            getBadPutDataMessageBytes(app0.getPersonas().single().entityId)
                        )
                        // Check that receiver gets the message and ignores it
                        it.waitUntil("Error handling message: java.lang.AssertionError: Invalid id")
                    }
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