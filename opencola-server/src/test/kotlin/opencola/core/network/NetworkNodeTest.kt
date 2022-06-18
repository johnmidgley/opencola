package opencola.core.network

import opencola.core.TestApplication
import opencola.core.model.Authority
import opencola.core.network.zerotier.ZeroTierClient
import opencola.server.PeerTest
import org.kodein.di.instance
import kotlin.test.assertEquals
import kotlin.test.assertFalse


class NetworkNodeTest : PeerTest() {
    private val ztAuthToken = "THx5SAwGhzwiWSXUWfDjv073qF8u3mz0"
    // @Test
    fun testInvalidToken(){
        val networkNode by TestApplication.instance.injector.instance<NetworkNode>()
        assertFalse(networkNode.isNetworkTokenValid(""))
    }

    // Get or create an application instance that will live across test runs. This avoids hammering ZeroTier when
    // just testing communication between nodes.


    private fun deleteAllNetworks(){
        val zeroTierClient = ZeroTierClient(ztAuthToken)
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

    // @Test
//    fun testLibZTSockets() {
//        deleteAllNetworks()
//
//        val app0 = getPersistentApplication(0)
//        val rootAuthority0 by app0.injector.instance<Authority>()
//        val networkNode0 by app0.injector.instance<NetworkNode>()
//        val addressBook0 by app0.injector.instance<AddressBook>()
//
//        networkNode0.start()
//        val authority0 = addressBook0.getAuthority(rootAuthority0.entityId)!!
//        assertEquals(authority0, addressBook0.getAuthorities().single())
//
//        val app1 = getPersistentApplication(1)
//        val rootAuthority1 by app1.injector.instance<Authority>()
//        val networkNode1 by app1.injector.instance<NetworkNode>()
//        val addressBook1 by app1.injector.instance<AddressBook>()
//
//        networkNode1.start()
//        val authority1 = addressBook1.getAuthority(rootAuthority1.entityId)!!
//        assertEquals(authority1, addressBook1.getAuthorities().single())
//
//        val inviteToken0 = networkNode0.getInviteToken()
//        networkNode1.addPeer(inviteToken0)
//
//        val app1peer0 = addressBook1.getAuthority(authority0.entityId)
//        assertNotNull(app1peer0)
//        assertAuthoritiesAreSame(authority0, app1peer0)
//
//        val inviteToken1 = networkNode1.getInviteToken()
//        networkNode0.addPeer(inviteToken1)
//
//        val app0peer1 = addressBook0.getAuthority(authority1.entityId)
//        assertNotNull(app0peer1)
//        assertAuthoritiesAreSame(authority1, app0peer1)
//    }

    // @Test
    fun testZtLibPeers() {
        ProcessNode.stopAllNodes()

        // Start a peer in another process - needed to have a distinct libzt node address
        val node0 = ProcessNode.getNode(0).start()

        // Directly interact with a local "node" (easier for debugging than having both nodes in outside processes)
        val node1 = ApplicationNode.getNode(0).start()
        node1.setNetworkToken(ztAuthToken)

        node0.stop()
        node1.stop()
    }
}