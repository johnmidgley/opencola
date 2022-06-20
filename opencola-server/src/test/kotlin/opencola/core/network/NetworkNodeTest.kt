package opencola.core.network

import opencola.core.TestApplication
import opencola.core.config.Config
import opencola.core.config.NetworkConfig
import opencola.core.config.setZeroTierIntegration
import opencola.core.model.Authority
import opencola.core.network.zerotier.ZeroTierAddress
import opencola.core.network.zerotier.ZeroTierClient
import opencola.server.PeerTest
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull


class NetworkNodeTest : PeerTest() {
    private val ztAuthToken = "THx5SAwGhzwiWSXUWfDjv073qF8u3mz0"
    private val zeroTierClient = ZeroTierClient(ztAuthToken)
    // @Test
    fun testInvalidToken(){
        val networkNode by TestApplication.instance.injector.instance<NetworkNode>()
        assertFalse(networkNode.isNetworkTokenValid(""))
    }

    // Get or create an application instance that will live across test runs. This avoids hammering ZeroTier when
    // just testing communication between nodes.


    private fun deleteAllNetworks(){
        zeroTierClient.getNetworks().forEach{
            zeroTierClient.deleteNetwork(it.id!!)
        }
    }

    private fun assertAuthoritiesAreSame(authority0: Authority, authority1: Authority){
        assertEquals(authority0.entityId, authority1.entityId)
        assertEquals(authority0.name, authority1.name)
        assertEquals(authority0.uri, authority1.uri)
        assertEquals(authority0.publicKey, authority1.publicKey)
        assertEquals(authority0.imageUri, authority1.imageUri)
    }

    private fun getApplicationNode(num: Int, zeroTierIntegrationEnabled: Boolean): Node {
        val config = ApplicationNode.getBaseConfig().setZeroTierIntegration(zeroTierIntegrationEnabled)
        return ApplicationNode.getNode(num, config = config)
    }

    @Test
    fun testEnableZeroTierIntegration() {
        getApplicationNode(0, false).also {
            it.start()
            assertEquals("", it.getPeers().results.single().address)
            it.stop()
        }

        getApplicationNode(0, true).also {
            it.start()
            val zeroTierAddress = ZeroTierAddress.fromURI(URI(it.getPeers().results.single().address))
            assertNotNull(zeroTierAddress)
            assertNotNull(zeroTierAddress.nodeId)
            it.stop()
        }
    }


    // @Test
    fun testZtLibPeers() {
        ProcessNode.stopAllNodes()
        deleteAllNetworks()
        assert(zeroTierClient.getNetworks().isEmpty())

        // Create Node
        val node0 = ApplicationNode.getNode(0).start()

        // Verify authority address without network token
        val authority0 = node0.getPeers().results.single()
        val ztAddress0 = ZeroTierAddress.fromURI(URI(authority0.address))!!
        assert(ztAddress0.networkId == null)
        assertNotNull(ztAddress0.nodeId)

        // Add token, which should trigger a network creation
        node0.setNetworkToken(ztAuthToken)
        val network0 = zeroTierClient.getNetworks().single()

        // Verify authority address with network token
        val authority1 = node0.getPeers().results.single()
        val ztAddress1 = ZeroTierAddress.fromURI(URI(authority1.address))!!
        assertNotNull(ztAddress1.networkId)
        assertNotNull(ztAddress1.nodeId)
        assertEquals(network0.id, ztAddress1.networkId)

        // Verify that authority is part of the network
        val member0 = zeroTierClient.getNetworkMember(ztAddress1.networkId!!, ztAddress1.nodeId!!)
        assertNotNull(member0.name)

        val inviteToken1 = InviteToken.decode(node0.getInviteToken())
        val ztAddress2 = ZeroTierAddress.fromURI(inviteToken1.address)!!
        assertNotNull(ztAddress2.networkId)
        assertNotNull(ztAddress2.nodeId)
        assertEquals(network0.id, ztAddress2.networkId)

        node0.stop()

        // Create second node to accept invite
        val node1 = ApplicationNode.getNode(1).start()
        val authority2 = node1.getPeers().results.single()
        val ztAddress3 = ZeroTierAddress.fromURI(URI(authority2.address))!!
        assertNotEquals(ztAddress3.nodeId, ztAddress2.nodeId)

        // Accept invite



    }
}