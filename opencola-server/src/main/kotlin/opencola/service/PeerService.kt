package opencola.service

import mu.KotlinLogging
import opencola.core.config.Application
import opencola.core.config.NetworkConfig
import opencola.core.config.PeerConfig
import opencola.core.model.Id
import opencola.core.network.Peer
import opencola.core.storage.EntityStore

class PeerService(private val networkConfig: NetworkConfig, private val entityStore: EntityStore) {
    private val logger = KotlinLogging.logger{}
    private val peers = peersFromNetworkConfig(networkConfig)

    init{
        // TODO: inform peers of being online and update status for these peers
    }

    // TODO: Peers should eventually come from a private part of the entity store
    private fun peersFromNetworkConfig(networkConfig: NetworkConfig): Map<Id, Peer> {
        return networkConfig.peers.map{
            val peerId = Id.fromHexString(it.id)
            Pair(peerId, Peer(Id.fromHexString(it.id), it.name, it.host, Peer.Status.Offline))
        }.toMap()
    }

    fun updatePeerStatus(id: Id, status: Peer.Status){
        val peer = peers[id]

        if(peer == null)
            logger.warn { "Attempt to update status of unknown peer: $id" }
        else {
            peer.status = status
        }
    }

    fun requestTransactions(peer: Peer){
        // TODO: Update getTransaction to take authorityId
        val currentTransactionId = entityStore.getTransactionId(peer.id)
    }

}