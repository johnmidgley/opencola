package opencola.server.handlers

import opencola.core.TestApplication
import kotlin.test.*

class PeerTest {

    @Test
    fun testSetEmptyAddress() {
        val app = TestApplication.instance
        val authority = app.getPersonas().first()
        val peer = getPeers(authority, app.inject()).results.first()
        val updatedPeer = Peer(peer.id, peer.name, peer.publicKey, "", peer.imageUri, peer.isActive)
        assertFails { updatePeer(authority.entityId, app.inject(), app.inject(), app.inject(), updatedPeer) }
    }
}