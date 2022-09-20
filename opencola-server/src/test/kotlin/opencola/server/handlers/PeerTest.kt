package opencola.server.handlers

import io.opencola.core.model.Authority
import opencola.core.TestApplication
import kotlin.test.*

class PeerTest {

    @Test
    fun testSetEmptyAddress() {
        val app = TestApplication.instance
        val peer = getPeers(app.inject(), app.inject()).results.first()
        val updatedPeer = Peer(peer.id, peer.name, peer.publicKey, "", peer.imageUri, peer.isActive)
        assertFails { updatePeer(app.inject<Authority>().entityId, app.inject(), app.inject(), app.inject(), updatedPeer) }
    }
}