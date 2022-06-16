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
import java.nio.file.Path

private val logger = KotlinLogging.logger("NetworkNode")

class NetworkNode(private val storagePath: Path, private val authorityId: Id, private val addressBook: AddressBook, private val encryptor: Encryptor) {
    // TODO: Make install script put the platform dependent version of libzt in the right place. On mac, it needs to be
    //  put in ~/Library/Java/Extensions/ (or try /Library/Java/Extensions/ globally)
    //  Need to figure out where it goes on Linux / Windows
    private val node = ZeroTierNode()

    init {
        if(getAuthToken() == null){
            logger.warn { "No network token specified. Cannot manage peer connections." }
        }
    }

    private fun getAuthToken() : String? {
        return addressBook.getAuthority(authorityId)?.networkToken?.let { String(encryptor.decrypt(authorityId, it)) }
    }

    private fun zeroTierClient(): ZeroTierClient {
        // We create the client on each use, because the authToken is mutable
        val authToken = getAuthToken()
            ?: throw IllegalStateException("Can't provide a ZeroTierClient without an authToken. Set the root user's network token in peer settings.")

        return ZeroTierClient(authToken)
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

    private fun createNodeNetwork(zeroTierClient: ZeroTierClient, authority: Authority) : Network {
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

    private fun getOrCreateNodeNetwork(zeroTierClient: ZeroTierClient, authority: Authority) : Network {
        val networkName = getNetworkName()
        val networks = zeroTierClient.getNetworks().filter { it.config?.name == networkName }

        if(networks.size > 1) {
            throw IllegalStateException("Multiple networks exist with the root name: $networkName. Fix at ZT Central")
        }

        return networks.singleOrNull() ?: createNodeNetwork(zeroTierClient, authority)
    }

    private fun initNodeNetwork(zeroTierClient: ZeroTierClient) {
        val authority = addressBook.getAuthority(authorityId)
            ?: throw IllegalStateException("Root authority not found in address book")

        val network = getOrCreateNodeNetwork(zeroTierClient, authority)
        authority.uri = ZeroTierAddress(network.id, getId()).toURI()
        addressBook.updateAuthority(authority)
    }

    fun start() {
        logger.info { "Starting..." }
        node.initFromStorage(storagePath.toString())
        node.initSetEventHandler(MyZeroTierEventListener())
        node.start()

        while (!node.isOnline) {
            // TODO: Break infinite loop
            ZeroTierNative.zts_util_delay(50);
        }

        initNodeNetwork(zeroTierClient())
        logger.info { "Started: ${node.id.toString(16)}" }
    }

    fun stop() {
        logger.info { "Stopping." }
        node.stop()
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

    private fun addZeroTierPeer(peer: Authority){
        val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return
        val zeroTierClient = getAuthToken()?.let { ZeroTierClient(it) }

        if(zeroTierAddress.nodeId == null){
            throw IllegalArgumentException("Can't add peer with no nodeId: $zeroTierAddress")
        }

        if(zeroTierAddress.networkId == null && zeroTierClient == null) {
            throw IllegalArgumentException("Can't add peer with that has no network without having a local network")
        }

        if(zeroTierAddress.networkId != null){
            // When a networkId is specified, we must join the peer's network to communicate. The peer should grant
            // this node access to the network on their end
            logger.info { "Joining network: ${zeroTierAddress.networkId}" }
            joinNetwork(zeroTierAddress.networkId)
        }

        if (zeroTierClient != null) {
            // Allow the peer node onto our network
            val networkId =
                addressBook.getAuthority(authorityId)?.uri.nullOrElse { ZeroTierAddress.fromURI(it) }?.networkId ?:
                throw IllegalArgumentException("Unable to determine local network id to add peer to. Check peer settings")

            logger.info { "Adding peer to local node network" }
            zeroTierClient.addNetworkMember(networkId, zeroTierAddress.nodeId, authorityToMember(peer))
        }
    }

    fun addPeer(inviteToken: InviteToken){
        logger.info { "Adding peer: $inviteToken" }
        val existingPeer = addressBook.getAuthority(inviteToken.authorityId)

        if(existingPeer != null) {
            logger.info { "Found existing peer - updating" }
            if(existingPeer.uri != inviteToken.address) {
                // Since address is being updated, remove zero tier connection for old address
                removeZeroTierPeer(existingPeer)
            }

            existingPeer.name = inviteToken.name
            existingPeer.publicKey = inviteToken.publicKey
            existingPeer.uri = inviteToken.address
            existingPeer.imageUri = inviteToken.imageUri
        }

        val peer = existingPeer ?: inviteToken.toAuthority(authorityId)
        addZeroTierPeer(peer)
        addressBook.updateAuthority(peer)
    }

    private fun removeZeroTierPeer(peer: Authority) {
        val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return
        if (zeroTierAddress.networkId != null) {
            // When we connected, we joined the peer's network, so we need to now leave it
            leaveNetwork(zeroTierAddress.networkId)
        } else if (zeroTierAddress.nodeId != null) {
            // Remove the peer from our network
            val networkId = addressBook
                .getAuthority(authorityId)?.uri.nullOrElse { ZeroTierAddress.fromURI(it) }?.networkId
                ?: throw IllegalArgumentException("Cannot manage peers without a root networkId. Check peer settings")

            zeroTierClient().deleteNetworkMember(networkId, zeroTierAddress.nodeId)
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

internal class MyZeroTierEventListener : ZeroTierEventListener {
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