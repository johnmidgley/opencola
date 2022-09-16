package opencola.core.network.providers.relay

import io.opencola.core.model.Authority
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.network.Request
import io.opencola.core.security.generateKeyPair
import io.opencola.core.storage.AddressBook
import io.opencola.relay.server.startWebServer
import opencola.server.PeerTest
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OCRelayNetworkProviderTest : PeerTest() {
    private val ocRelayUri = URI("ocr://0.0.0.0:8080")

    @Test
    fun testOCRelayNetworkProvider() {
        println("Getting applications")
        val (app0, app1) = getApplications(2)

        println("Setting Relay Addresses")
        val addressBook0 = app0.inject<AddressBook>()
        val peer1 = addressBook0.getAuthority(app1.inject<Authority>().entityId)!!
        peer1.uri = ocRelayUri
        addressBook0.updateAuthority(peer1)

        val addressBook1 = app1.inject<AddressBook>()
        val peer0 = addressBook1.getAuthority(app0.inject<Authority>().entityId)!!
        peer0.uri = ocRelayUri
        addressBook1.updateAuthority(peer0)

        println("Starting relay server")
        val webServer = startWebServer(8080)
        println("Starting network node0")
        val networkNode0 = app0.inject<NetworkNode>().also { it.start() }
        println("Starting network node1")
        val networkNode1 = app1.inject<NetworkNode>().also { it.start() }

        try {
            println("Testing ping")
            run {
                val response =
                    networkNode0.sendRequest(peer1.entityId, Request(peer0.entityId, Request.Method.GET, "/ping"))
                assertNotNull(response)
                assertEquals("pong", response.message)
            }

            println("Testing bad 'from' id")
            run {
                val response =
                    networkNode0.sendRequest(Id.new(), Request(peer0.entityId, Request.Method.GET, "/ping"))
                assertNull(response)
            }

            println("Testing wrong public key on recipient side")
            run {
                val peer = addressBook1.getAuthority(peer0.entityId)!!
                peer.publicKey = generateKeyPair().public
                addressBook1.updateAuthority(peer)
                val response =
                    networkNode0.sendRequest(peer1.entityId, Request(peer0.entityId, Request.Method.GET, "/ping"))
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
}