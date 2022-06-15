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

    private fun createNodeNetwork(zeroTierClient: ZeroTierClient, authority: Authority) : Network {
        val networkConfig = NetworkConfig.forCreate(
            name = "root:$authorityId",
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

        return createdNetwork
    }

    private fun initNodeNetwork(zeroTierClient: ZeroTierClient) {
        val authority = addressBook.getAuthority(authorityId)
            ?: throw IllegalStateException("Root authority not found in address book")

        if(authority.uri == null || authority.uri.toString().isBlank()) {
            logger.info { "No address found for root authority - creating network" }
            val networkId = createNodeNetwork(zeroTierClient, authority).id!!
            val nodeId = node.id.toString(16)
            zeroTierClient.addNetworkMember(networkId, nodeId, authorityToMember(authority))
            authority.uri = ZeroTierAddress(networkId, nodeId).toURI()
            addressBook.updateAuthority(authority)
        }
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

    fun addPeer(peer: Authority){
        addressBook.updateAuthority(peer)
        val address = peer.uri ?: return
        val ztAddress = ZeroTierAddress.fromURI(address) ?: return

        if(ztAddress.networkId != null){
            // When a network Id is specified, we must join the peer's network to communicate. The peer should grant
            // this node access to the network on their end
            joinNetwork(ztAddress.networkId)
        } else if (ztAddress.nodeId != null) {
            // No network was specified, so we must authorize the peer on this node's network
            val networkId = addressBook
                .getAuthority(authorityId)?.uri.nullOrElse { ZeroTierAddress.fromURI(it) }?.networkId ?:
                throw IllegalArgumentException("Can't add a ZT peer without a ZT address with network for the root authority. Check peer settings")

            zeroTierClient().addNetworkMember(networkId, ztAddress.nodeId, authorityToMember(peer))
        }
    }

    fun joinNetwork(address: String){
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.join(id)
    }

    fun leaveNetwork(address: String) {
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.leave(id)
    }

    fun getId(): String {
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