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

    fun setAuthorityAddressToRelay(authorityId: Id, addressBook: AddressBook) : Authority {
        val authority = addressBook.getAuthority(authorityId)!!
        authority.uri = ocRelayUri
        return addressBook.updateAuthority(authority)
    }

    @Test
    fun testOCRelayNetworkProvider() {
        println("Getting applications")
        val (app0, app1) = getApplications(2)

        println("Setting Relay Addresses")
        val peer1 = setAuthorityAddressToRelay(app1.inject<Authority>().entityId, app0.inject())
        val peer0 = setAuthorityAddressToRelay(app0.inject<Authority>().entityId, app1.inject())

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
                val addressBook1 = app1.inject<AddressBook>()
                val peer = addressBook1.getAuthority(peer0.entityId)!!
                peer.publicKey = generateKeyPair().public
                addressBook1.updateAuthority(peer)
                val response =
                    networkNode0.sendRequest(peer1.entityId, Request(peer0.entityId, Request.Method.GET, "/ping"))
                assertNotNull(response)
                assertEquals(400, response.status)
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

        val relayServer = startWebServer(8080)

        setAuthorityAddressToRelay(app0.inject<Authority>().entityId, app0.inject() )
        setAuthorityAddressToRelay(app1.inject<Authority>().entityId, app1.inject() )

        try {
            testConnectAndReplicate(application0, application1)
        } finally {
            application0.stop()
            application1.stop()
            relayServer.stop(200,200)
        }
    }
}