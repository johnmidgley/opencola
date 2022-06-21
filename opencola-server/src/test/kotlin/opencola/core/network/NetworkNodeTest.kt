package opencola.core.network

import opencola.core.TestApplication
import opencola.core.config.setZeroTierIntegration
import opencola.core.io.readStdOut
import opencola.core.model.Authority
import opencola.core.network.zerotier.ZeroTierAddress
import opencola.core.network.zerotier.ZeroTierClient
import opencola.core.security.encode
import opencola.core.storage.AddressBook
import opencola.server.PeerTest
import opencola.server.handlers.Peer
import org.junit.Test
import org.kodein.di.instance
import java.net.URI
import kotlin.test.*


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
        assert(zeroTierClient.getNetworks().isEmpty())
    }

    private fun assertAuthoritiesAreSame(authority0: Authority, authority1: Authority){
        assertEquals(authority0.entityId, authority1.entityId)
        assertEquals(authority0.name, authority1.name)
        assertEquals(authority0.uri, authority1.uri)
        assertEquals(authority0.publicKey, authority1.publicKey)
        assertEquals(authority0.imageUri, authority1.imageUri)
    }

    private fun getApplicationNode(num: Int, zeroTierIntegrationEnabled: Boolean): ApplicationNode {
        val config = ApplicationNode.getBaseConfig().setZeroTierIntegration(zeroTierIntegrationEnabled)
        return ApplicationNode.getNode(num, config = config)
    }

    // @Test
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
    fun testInviteWithNoAddressFails(){
        getApplicationNode(0, false).also {
            it.start()
            assertEquals("", it.getPeers().results.single().address)
            assertFails { it.getInviteToken() }
            it.stop()
        }
    }

    // @Test
    fun testInviteSelfFails() {
        val node0 = getApplicationNode(0, true).start()
        val token = node0.getInviteToken()
        assertFails { node0.postInviteToken(token) }

        node0.stop()
    }

    // @Test
    fun testInviteBetweenNoZTAuthNodesFails() {
        val node0 = getApplicationNode(0, true).start()
        val token = node0.getInviteToken()
        node0.stop()

        val node1 = getApplicationNode(1, true).start()
        val peer0 = node1.postInviteToken(token)

        // Neither node is hosting a network, so should fail
        assertFails { node1.updatePeer(peer0) }
        node1.stop()
    }

    private fun getRootAuthorityFromAddressBook(node: ApplicationNode): Authority {
        val authorityId = node.application.inject<Authority>().entityId
        return node.application.inject<AddressBook>().getAuthority(authorityId)!!
    }

    private fun verifyAuthorityEqualsPeer(authority: Authority, peer: Peer) {
        assertEquals(authority.name, peer.name)
        assertEquals(authority.publicKey!!.encode(), peer.publicKey)
        assertEquals(authority.uri.toString(), peer.address)
        assertEquals(authority.imageUri?.toString(), peer.imageUri)
    }

    private fun verifyZTNetwork(hostAuthority: Authority, peerAuthority: Authority){
        // Verify that host and peer are on the host network
        val hostZeroTierAddress = ZeroTierAddress.fromURI(hostAuthority.uri!!)!!
        val peerZeroTierAddress = ZeroTierAddress.fromURI(peerAuthority.uri!!)!!

        val members = zeroTierClient.getNetworkMembers(hostZeroTierAddress.networkId!!)
        val hostMember = members.singleOrNull{ it.nodeId == hostZeroTierAddress.nodeId }
        assertNotNull(hostMember)
        assertEquals(hostAuthority.name, hostMember.name)
        assertEquals(true, hostMember.config?.authorized)
        val member1 = members.singleOrNull{ it.nodeId == peerZeroTierAddress.nodeId }
        assertNotNull(member1)
        assertEquals(peerAuthority.name, member1.name)
        assertEquals(true, member1.config?.authorized)
    }

    private fun testInviteWithZTNodes(setBothAuthTokens: Boolean) {
        deleteAllNetworks()

        val node0 = getApplicationNode(0, true).also { it.start() }
        node0.setNetworkToken(ztAuthToken)
        val inviteToken0 = node0.getInviteToken()
        node0.stop()

        // Node should now have its own network

        val node1 = getApplicationNode(1, true).also { it.start() }
        if (setBothAuthTokens) node1.setNetworkToken(ztAuthToken)
        val inviteToken1 = node1.getInviteToken()
        val peer0 = node1.postInviteToken(inviteToken0)
        node1.updatePeer(peer0)

        // Node0 should now be a peer of Node1

        val authority0 = getRootAuthorityFromAddressBook(node0)
        val peer1 = node1.getPeers().results.single { it.id == authority0.entityId.toString() }
        verifyAuthorityEqualsPeer(authority0, peer1)

        readStdOut { line -> line.contains("EVENT_NETWORK_ACCESS_DENIED") }
        node1.stop()

        // Now add node1 as peer to node0

        node0.start()
        val peer2 = node0.postInviteToken(inviteToken1)
        node0.updatePeer(peer2)
        val authority1 = getRootAuthorityFromAddressBook(node1)
        val peer3 = node0.getPeers().results.single { it.id == authority1.entityId.toString() }
        verifyAuthorityEqualsPeer(authority1, peer3)

        // At this point, both nodes should be on both networks

        verifyZTNetwork(authority0, authority1)
        if (setBothAuthTokens) verifyZTNetwork(authority1, authority0)
    }

    // @Test
    fun testInviteWith1ZTAuthNode() {
        testInviteWithZTNodes(false)
    }

    //@Test
    fun testInviteWithBothZTAuthNodes() {
        testInviteWithZTNodes(true)
    }

    // @Test
    fun testZtLibPeers() {
        ProcessNode.stopAllNodes()
        // deleteAllNetworks()

        // Start nodes
        val node0 = getApplicationNode(0, true).also { it.make(); it.start() }
        node0.setNetworkToken(ztAuthToken)

        val node1 = ProcessNode.getNode(1).also { it.make(); it.start() }
        node1.setNetworkToken(ztAuthToken)

        // Connect nodes
        node0.updatePeer(node0.postInviteToken(node1.getInviteToken()))
        node1.updatePeer(node1.postInviteToken(node0.getInviteToken()))
    }
}