package opencola.core.network

import opencola.core.io.readStdOut
import opencola.core.model.Authority
import opencola.core.model.ResourceEntity
import opencola.core.network.PeerEvent.NewTransaction
import opencola.core.network.Request.Method.GET
import opencola.core.network.Request.Method.POST
import opencola.core.network.providers.zerotier.ZeroTierAddress
import opencola.core.network.providers.zerotier.ZeroTierClient
import opencola.core.network.providers.zerotier.ZeroTierNetworkProvider
import opencola.core.security.encode
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import opencola.server.PeerTest
import opencola.server.handlers.Peer
import opencola.server.handlers.TransactionsResponse
import opencola.server.handlers.inviteTokenToPeer
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull


class NetworkNodeTest : PeerTest() {
    private val ztAuthToken = "THx5SAwGhzwiWSXUWfDjv073qF8u3mz0"
    private val zeroTierClient = ZeroTierClient(ztAuthToken)

    // @Test
    fun testInvalidToken(){
        assertFalse(ZeroTierNetworkProvider.isNetworkTokenValid(""))
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

    private fun addPeer(applicationNode: ApplicationNode , peerApplicationNode: ApplicationNode) {
        val inviteToken = peerApplicationNode.getInviteToken()
        val authorityId = applicationNode.application.inject<Authority>().entityId
        val peer = inviteTokenToPeer(authorityId, inviteToken)
        applicationNode.updatePeer(peer)
    }

    @Test
    fun testHttpConnectAndReplicate(){
        val application0 = getApplicationNode(0, zeroTierIntegrationEnabled = false).also { it.start() }

        try {
            val authority0 = application0.application.inject<Authority>()
            val resource0 = ResourceEntity(authority0.entityId, URI("https://resource0"), "Resource0")
            val entityStore0 = application0.application.inject<EntityStore>().also { it.updateEntities(resource0) }

            val application1 = getApplicationNode(1, zeroTierIntegrationEnabled = false).also { it.start() }
            val authority1 = application1.application.inject<Authority>()
            val resource1 = ResourceEntity(authority1.entityId, URI("https://resource1"), "Resource1")
            val entityStore1 = application1.application.inject<EntityStore>().also { it.updateEntities(resource1) }

            addPeer(application0, application1)
            readStdOut { line -> line.contains("Completed requesting transactions from") }
            addPeer(application1, application0)

            // Connection should trigger two index operations from transaction sharing
            readStdOut { line -> line.contains("LuceneSearchIndex: Indexing") }
            readStdOut { line -> line.contains("LuceneSearchIndex: Indexing") }

            // Check that resources replicated as expected
            assertEquals(entityStore0.getEntity(resource1.authorityId, resource1.entityId)?.name, resource1.name)
            assertEquals(entityStore1.getEntity(resource0.authorityId, resource0.entityId)?.name, resource0.name)
        } finally {
            application0.stop()
        }
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

    private fun makeAndConnectNode0andNode1(): Pair<Node, Node> {
        // deleteAllNetworks()

        // Start nodes
        // TODO: Switch getApplicationNode to be a factory member of ApplicationNode, like ProcessNode does
        val node0 = ProcessNode.getNode(0).also { it.make(); it.start() }
        node0.setNetworkToken(ztAuthToken)

        // TODO: Add persistent flag to getNode constructor
        val node1 = ProcessNode.getNode(1).also { it.make(); it.start() }
        node1.setNetworkToken(ztAuthToken)

        // Connect nodes
        println("Adding peer node-1 to node-0")
        node0.updatePeer(node0.postInviteToken(node1.getInviteToken()))
        println("Adding peer node-0 to node-1")
        node1.updatePeer(node1.postInviteToken(node0.getInviteToken()))

        val node0Peers = node0.getPeers()
        val node0AuthorityId = node0Peers.authorityId
        val node1Peers = node1.getPeers()
        val node1AuthorityId = node1Peers.authorityId

        assertNotNull(node0Peers.results.single{ it.id == node1AuthorityId } )
        assertNotNull(node1Peers.results.single{ it.id == node0AuthorityId } )

        return Pair(node0, node1)
    }

    // @Test
    fun testZtLibPeers() {
        ProcessNode.stopAllNodes()
        deleteAllNetworks()
        val createNodes = true

        if (createNodes) {
            makeAndConnectNode0andNode1().also {
                it.first.stop()
                it.second.stop()
            }
        }

        ProcessNode.stopAllNodes()

        val (node0, _) = Pair(
            getApplicationNode(0, zeroTierIntegrationEnabled = true, persistent = true).also { it.start() },
            ProcessNode.getNode(1).also { it.start() }
        )

        try {
            val node0Authority = node0.application.inject<Authority>()
            val addressBook = node0.application.inject<AddressBook>()
            val peer1 = addressBook.getAuthorities().single { it.entityId != node0Authority.entityId }
            val networkNode0 = node0.application.inject<NetworkNode>()

            val pingRequest = Request(node0Authority.entityId, GET, "/ping")
            val pingResponse = networkNode0.sendRequest(peer1.entityId, pingRequest)
            assertNotNull(pingResponse)
            println("Response: $pingResponse")

            val transactionsRequest = Request(
                node0Authority.entityId,
                GET,
                "/transactions",
                null,
                mapOf("authorityId" to "5BLZBd3f1rWTYKax26h2DVZeo9j1UsBL2cHZLnmpSMmo")
            )
            val transactionsResponse = networkNode0.sendRequest(peer1.entityId, transactionsRequest)
            assertNotNull(transactionsResponse)
            println("Response: $transactionsResponse")
            println("Body: ${transactionsResponse.decodeBody<TransactionsResponse>()}")

            val notification = Notification(node0Authority.entityId, NewTransaction)
            val notificationRequest = request(node0Authority.entityId, POST, "/notifications", null, null, notification)
            val notificationResponse = networkNode0.sendRequest(peer1.entityId, notificationRequest)
            assertNotNull(notificationResponse)
            println("Response: $notificationResponse")

            // Sleep to let any background IO drain
            Thread.sleep(1000)

            println("Done")
        } catch (e: Exception) {
            println(e)
        }

    }
}