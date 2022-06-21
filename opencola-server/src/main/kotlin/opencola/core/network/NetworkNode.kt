package opencola.core.network

import com.zerotier.sockets.ZeroTierEventListener
import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierNode
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

private val logger = KotlinLogging.logger("NetworkNode")

class NetworkNode(
    private val config: OpenColaNetworkConfig,
    private val storagePath: Path,
    private val authorityId: Id,
    private val addressBook: AddressBook,
    private val encryptor: Encryptor
) {
    // TODO: Make install script put the platform dependent version of libzt in the right place. On mac, it needs to be
    //  put in ~/Library/Java/Extensions/ (or try /Library/Java/Extensions/ globally)
    //  Need to figure out where it goes on Linux / Windows
    private val node = ZeroTierNode()
    private var authority: Authority? = null
    private var authToken: String? = null
    private var zeroTierClient: ZeroTierClient? = null

    private fun setAuthority(authority: Authority) {
        val thisAuthority = this.authority
        val initRequired = thisAuthority == null || !authority.networkToken.contentEquals(thisAuthority.networkToken) || authority.uri != thisAuthority.uri
        this.authority = authority
        authToken = authority.networkToken.nullOrElse { String(encryptor.decrypt(authorityId, it)) }

        if(config.zeroIntegrationTierEnabled) {
            zeroTierClient = authToken.nullOrElse { ZeroTierClient(it) }

            if (initRequired)
                initNodeNetwork()
        }
    }

    private val addressUpdateHandler : (Authority) -> Unit = { peer ->
        if(peer.entityId == authorityId)
            setAuthority(peer)
    }

    fun isNetworkTokenValid(networkToken: String) : Boolean {
        try {
            ZeroTierClient(networkToken).getNetworks()
        }catch(e: Exception){
            logger.debug { e }
            return false
        }

        return true
    }

    private fun getNetworkName(): String {
        return "root:$authorityId"
    }

    // TODO: Should probably abstract out a NetworkProvider, and have ZeroTier be one type.
    private fun createNodeNetwork() : Network {
        val authority = authority!!
        val zeroTierClient = zeroTierClient!!
        val networkConfig = NetworkConfig.forCreate(
            name = getNetworkName(),
            private = true,
            // TODO: Figure out how this should be set. Just use ipv6? Is it even needed?
            // routes = listOf(Route("172.27.0.0/16")),
            // v4AssignMode = IPV4AssignMode(true),
            // ipAssignmentPools = listOf(IPRange("10.243.0.1", "10.243.255.254"))
        )
        val network = Network.forCreate(
            networkConfig,
            "Root network for: ${authority.name} ($authorityId)"
        )
        val createdNetwork = zeroTierClient.createNetwork(network)

        if(createdNetwork.id == null){
            throw RuntimeException("Unable to create root network")
        }

        zeroTierClient.addNetworkMember(createdNetwork.id, getId(), authorityToMember(authority))

        return createdNetwork
    }

    private fun getOrCreateNodeNetwork() : Network? {
        zeroTierClient ?: return null

        val networkName = getNetworkName()
        val networks = zeroTierClient!!.getNetworks().filter { it.config?.name == networkName }

        if(networks.size > 1) {
            throw IllegalStateException("Multiple networks exist with the root name: $networkName. Fix at ZT Central")
        }

        return networks.singleOrNull() ?: createNodeNetwork()
    }

    private fun initNodeNetwork() {
        if(config.zeroIntegrationTierEnabled) {
            val networkId = getOrCreateNodeNetwork()?.id
            val authority = authority!!
            authority.uri = ZeroTierAddress(networkId, getId()).toURI()
            addressBook.updateAuthority(authority)
        }
    }

    fun start() {
        if(config.zeroIntegrationTierEnabled) {
            logger.info { "Starting ZeroTier node..." }
            node.initFromStorage(storagePath.toString())
            node.initSetEventHandler(OCZeroTierEventListener())
            node.start()

            while (!node.isOnline) {
                // TODO: Break infinite loop
                ZeroTierNative.zts_util_delay(50);
            }

            authority = addressBook.getAuthority(authorityId)
                ?: throw IllegalArgumentException("Root authority not in AddressBook: $authorityId")
            initNodeNetwork()
            addressBook.addUpdateHandler(addressUpdateHandler)

            logger.info { "Started: ${node.id.toString(16)}" }
        }
    }

    fun stop() {
        if(config.zeroIntegrationTierEnabled) {
            logger.info { "Stopping ZeroTier node." }
            addressBook.removeUpdateHandler(addressUpdateHandler)
            node.stop()
            logger.info { "Stopped: ${node.id.toString(16)}" }
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
        if(config.zeroIntegrationTierEnabled && peer.entityId != authorityId) {
            val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return

            if (zeroTierAddress.nodeId == null) {
                throw IllegalArgumentException("Can't add peer with no nodeId: $zeroTierAddress")
            }

            if (zeroTierAddress.networkId == null && zeroTierClient == null) {
                throw IllegalArgumentException("Can't add peer with that has no network without having a local network")
            }

            if (zeroTierAddress.networkId != null) {
                // When a networkId is specified, we must join the peer's network to communicate. The peer should grant
                // this node access to the network on their end
                logger.info { "Joining network: ${zeroTierAddress.networkId}" }
                joinNetwork(zeroTierAddress.networkId)
            }

            val zeroTierClient = this.zeroTierClient
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
        if(config.zeroIntegrationTierEnabled) {
            val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return
            if (zeroTierAddress.networkId != null) {
                // When we connected, we joined the peer's network, so we need to now leave it
                leaveNetwork(zeroTierAddress.networkId)
            } else if (zeroTierAddress.nodeId != null) {
                // Remove the peer from our network
                val networkId = addressBook
                    .getAuthority(authorityId)?.uri.nullOrElse { ZeroTierAddress.fromURI(it) }?.networkId
                    ?: throw IllegalArgumentException("Cannot manage peers without a root networkId. Check peer settings")

                zeroTierClient!!.deleteNetworkMember(networkId, zeroTierAddress.nodeId)
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
        node.join(id)
    }

    private fun leaveNetwork(address: String) {
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.leave(id)
    }

    private fun getId(): String {
        return node.id.toString(16)
    }
}

internal class OCZeroTierEventListener : ZeroTierEventListener {
    override fun onZeroTierEvent(id: Long, eventCode: Int) {
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_UP) {
            logger.info("EVENT_NODE_UP")
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_ONLINE) {
            logger.info("EVENT_NODE_ONLINE: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_OFFLINE) {
            logger.info("EVENT_NODE_OFFLINE")
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NODE_DOWN) {
            logger.info("EVENT_NODE_DOWN")
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP4) {
            logger.info("ZTS_EVENT_NETWORK_READY_IP4: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP6) {
            logger.info("ZTS_EVENT_NETWORK_READY_IP6: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_DOWN) {
            logger.info("EVENT_NETWORK_DOWN: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_OK) {
            logger.info("EVENT_NETWORK_OK: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_ACCESS_DENIED) {
            logger.info("EVENT_NETWORK_ACCESS_DENIED: " + java.lang.Long.toHexString(id))
        }
        if (eventCode == ZeroTierNative.ZTS_EVENT_NETWORK_NOT_FOUND) {
            logger.info("EVENT_NETWORK_NOT_FOUND: " + java.lang.Long.toHexString(id))
        }
    }
}