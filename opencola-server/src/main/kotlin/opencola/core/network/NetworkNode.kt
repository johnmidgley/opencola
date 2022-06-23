package opencola.core.network

import mu.KotlinLogging
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.Id
import opencola.core.network.zerotier.*
import opencola.core.security.Encryptor
import opencola.core.serialization.LongByteArrayCodec
import opencola.core.storage.AddressBook
import opencola.server.handlers.Peer
import opencola.server.handlers.redactedNetworkToken
import java.nio.file.Path
import opencola.core.config.NetworkConfig as OpenColaNetworkConfig

class NetworkNode(
    private val config: OpenColaNetworkConfig,
    private val storagePath: Path,
    private val authorityId: Id,
    private val addressBook: AddressBook,
    private val encryptor: Encryptor
) {
    private val logger = KotlinLogging.logger("NetworkNode")
    // TODO: Make install script put the platform dependent version of libzt in the right place. On mac, it needs to be
    //  put in ~/Library/Java/Extensions/ (or try /Library/Java/Extensions/ globally)
    //  Need to figure out where it goes on Linux / Windows
    private var authority: Authority? = null // TODO: Make not nullable

    // TODO - Make nullable and only set when enabled (Search for all config.zeroTierProviderEnabled)
    private val zeroTierNetworkProvider = ZeroTierNetworkProvider(
        storagePath,
        addressBook.getAuthority(authorityId) ?: throw IllegalStateException("Root authority not in AddressBook"),
    )

    private fun setAuthority(authority: Authority) {
        this.authority = authority

        if(config.zeroTierProviderEnabled) {
            val authToken = authority.networkToken.nullOrElse { String(encryptor.decrypt(authorityId, it)) }

            zeroTierNetworkProvider.setNetworkToken(authToken)
            authority.uri = zeroTierNetworkProvider.getAddress()
            this.authority = addressBook.updateAuthority(authority)
        }
    }

    private val addressUpdateHandler : (Authority) -> Unit = { peer ->
        if(peer.entityId == authorityId)
            setAuthority(peer)
    }

    fun isNetworkTokenValid(networkToken: String) : Boolean {
        return zeroTierNetworkProvider.isNetworkTokenValid(networkToken)
    }

    fun start() {
        logger.info { "Starting..." }
        // TODO: Set during construction
        val authority = addressBook.getAuthority(authorityId)
            ?: throw IllegalArgumentException("Root authority not in AddressBook: $authorityId")
        this.authority = authority

        if(config.zeroTierProviderEnabled) {
            zeroTierNetworkProvider.start()
            authority.uri = zeroTierNetworkProvider.getAddress()
            this.authority = addressBook.updateAuthority(authority)
        }

        addressBook.addUpdateHandler(addressUpdateHandler)
        logger.info { "Started" }
    }

    fun stop() {
        logger.info { "Stopping..." }
        addressBook.removeUpdateHandler(addressUpdateHandler)
        if(config.zeroTierProviderEnabled) {
            zeroTierNetworkProvider.stop()
        }
        logger.info { "Stopped" }
    }

    fun getInviteToken() : InviteToken {
        val authority = addressBook.getAuthority(authorityId)
            ?: throw IllegalStateException("Root authority not found - can't generate invite token")
        return InviteToken.fromAuthority(authority)
    }

    fun inviteTokenToPeer(inviteToken: String) : Peer {
        val decodedInviteToken = InviteToken.decodeBase58(inviteToken)
        val imageUri = if(decodedInviteToken.imageUri.toString().isBlank()) null else decodedInviteToken.imageUri

        if(decodedInviteToken.authorityId == authorityId)
            throw IllegalArgumentException("You can't invite yourself (┛ಠ_ಠ)┛彡┻━┻")

        return Peer(
            decodedInviteToken.authorityId,
            decodedInviteToken.name,
            decodedInviteToken.publicKey,
            decodedInviteToken.address,
            imageUri,
            true,
            null,
        )
    }

    fun updatePeer(peer: Peer) {
        logger.info { "Updating peer: $peer" }

        val peerAuthority = peer.toAuthority(authorityId, encryptor)
        val existingPeerAuthority = addressBook.getAuthority(peerAuthority.entityId)

        if(existingPeerAuthority != null) {
            logger.info { "Found existing peer - updating" }

            if(existingPeerAuthority.publicKey != peerAuthority.publicKey){
                throw NotImplementedError("Updating publicKey is not currently implemented")
            }

            if(existingPeerAuthority.uri != peerAuthority.uri) {
                // Since address is being updated, remove zero tier connection for old address
                if (config.zeroTierProviderEnabled) zeroTierNetworkProvider.removePeer(existingPeerAuthority)
            }

            // TODO: Should there be a general way to do this? Add an update method to Entity or Authority?
            existingPeerAuthority.name = peerAuthority.name
            existingPeerAuthority.publicKey = peerAuthority.publicKey
            existingPeerAuthority.uri = peerAuthority.uri
            existingPeerAuthority.imageUri = peerAuthority.imageUri
            existingPeerAuthority.tags = peerAuthority.tags
            existingPeerAuthority.networkToken = peerAuthority.networkToken
        }

        if(peer.networkToken != null){
            if(peerAuthority.entityId != authorityId){
                throw IllegalArgumentException("Attempt to set networkToken for non root authority")
            }

            if(peer.networkToken != redactedNetworkToken) {
                if(!isNetworkTokenValid(peer.networkToken)){
                    throw IllegalArgumentException("Network token provided is not valid: ${peer.networkToken}")
                }

                peerAuthority.networkToken = encryptor.encrypt(authorityId, peer.networkToken.toByteArray())
            }
        }

        val peer = existingPeerAuthority ?: peerAuthority
        if(config.zeroTierProviderEnabled) zeroTierNetworkProvider.updatePeer(peer)
        addressBook.updateAuthority(peer)
    }

    private fun removePeer(peerId: Id){
        logger.info { "Removing peer: $peerId" }
        val peer = addressBook.getAuthority(peerId)

        if(peer == null){
            logger.info { "No peer found - ignoring" }
            return
        }

        if(config.zeroTierProviderEnabled) zeroTierNetworkProvider.removePeer(peer)
        addressBook.deleteAuthority(peerId)
    }
}