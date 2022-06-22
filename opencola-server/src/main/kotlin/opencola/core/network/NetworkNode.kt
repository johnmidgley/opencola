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
    private val zeroTierNetworkProvider = ZeroTierNetworkProvider(storagePath, addressBook)

    private fun setAuthority(authority: Authority) {
        val thisAuthority = this.authority
        val initRequired = thisAuthority == null || !authority.networkToken.contentEquals(thisAuthority.networkToken) || authority.uri != thisAuthority.uri
        this.authority = authority
        zeroTierNetworkProvider.authToken = authority.networkToken.nullOrElse { String(encryptor.decrypt(authorityId, it)) }

        if(config.zeroTierProviderEnabled) {
            zeroTierNetworkProvider.zeroTierClient = zeroTierNetworkProvider.authToken.nullOrElse { ZeroTierClient(it) }

            if (initRequired) {
                zeroTierNetworkProvider.initNodeNetwork()
            }
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
        zeroTierNetworkProvider.authority = authority

        if(config.zeroTierProviderEnabled) {
            zeroTierNetworkProvider.start()
            authority.uri = zeroTierNetworkProvider.getAddress()
            this.authority = addressBook.updateAuthority(authority)
        }

        addressBook.addUpdateHandler(addressUpdateHandler)
        logger.info { "Started" }
    }

    fun stop() {
        if(config.zeroTierProviderEnabled) {
            logger.info { "Stopping ZeroTier node." }
            addressBook.removeUpdateHandler(addressUpdateHandler)
            zeroTierNetworkProvider.node.stop()
            logger.info { "Stopped: ${zeroTierNetworkProvider.node.id.toString(16)}" }
        }
    }

    private fun authorityToMember(authority: Authority): Member {
        return Member.forCreate(
            authority.name ?: authority.entityId.toString(),
            MemberConfig.forCreate(authorized = true),
            hidden = false,
            description = "OpenCola Id: ${authority.entityId}"
        )
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

    private fun addZeroTierPeer(peer: Authority){
        if(config.zeroTierProviderEnabled && peer.entityId != authorityId) {
            val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return

            if (zeroTierAddress.nodeId == null) {
                throw IllegalArgumentException("Can't add peer with no nodeId: $zeroTierAddress")
            }

            if (zeroTierAddress.networkId == null && zeroTierNetworkProvider.zeroTierClient == null) {
                throw IllegalArgumentException("Can't add peer with that has no network without having a local network")
            }

            if (zeroTierAddress.networkId != null) {
                // When a networkId is specified, we must join the peer's network to communicate. The peer should grant
                // this node access to the network on their end
                logger.info { "Joining network: ${zeroTierAddress.networkId}" }
                joinNetwork(zeroTierAddress.networkId)
            }

            val zeroTierClient = this.zeroTierNetworkProvider.zeroTierClient
            if (zeroTierClient != null) {
                // Allow the peer node onto our network
                val networkId =
                    addressBook.getAuthority(authorityId)?.uri.nullOrElse { ZeroTierAddress.fromURI(it) }?.networkId
                        ?: throw IllegalArgumentException("Unable to determine local network id to add peer to. Check peer settings")

                logger.info { "Adding peer to local node network" }
                zeroTierClient.addNetworkMember(networkId, zeroTierAddress.nodeId, authorityToMember(peer))
            }
        }
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
                removeZeroTierPeer(existingPeerAuthority)
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
        addZeroTierPeer(peer)
        addressBook.updateAuthority(peer)
    }

    private fun removeZeroTierPeer(peer: Authority) {
        if(config.zeroTierProviderEnabled) {
            val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return
            if (zeroTierAddress.networkId != null) {
                // When we connected, we joined the peer's network, so we need to now leave it
                leaveNetwork(zeroTierAddress.networkId)
            } else if (zeroTierAddress.nodeId != null) {
                // Remove the peer from our network
                val networkId = addressBook
                    .getAuthority(authorityId)?.uri.nullOrElse { ZeroTierAddress.fromURI(it) }?.networkId
                    ?: throw IllegalArgumentException("Cannot manage peers without a root networkId. Check peer settings")

                zeroTierNetworkProvider.zeroTierClient!!.deleteNetworkMember(networkId, zeroTierAddress.nodeId)
            }
        }
    }

    private fun removePeer(peerId: Id){
        logger.info { "Removing peer: $peerId" }
        val peer = addressBook.getAuthority(peerId)

        if(peer == null){
            logger.info { "No peer found - ignoring" }
            return
        }

        removeZeroTierPeer(peer)
        addressBook.deleteAuthority(peerId)
    }

    private fun joinNetwork(address: String){
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        zeroTierNetworkProvider.node.join(id)
    }

    private fun leaveNetwork(address: String) {
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        zeroTierNetworkProvider.node.leave(id)
    }

    private fun getId(): String {
        return zeroTierNetworkProvider.node.id.toString(16)
    }
}