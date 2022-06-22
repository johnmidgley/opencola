package opencola.core.network.zerotier

import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierNode
import mu.KotlinLogging
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.network.NetworkProvider
import opencola.core.network.OCZeroTierEventListener
import opencola.core.serialization.LongByteArrayCodec
import java.net.URI
import java.nio.file.Path

class ZeroTierNetworkProvider(
    private val storagePath: Path,
    private val authority: Authority,
    authToken: String?,
) : NetworkProvider {
    private val logger = KotlinLogging.logger("ZeroTierNetworkProvider")
    private val node = ZeroTierNode()
    private var networkId: String? = null
    private var zeroTierClient: ZeroTierClient? = null

    init {
        authToken.nullOrElse { zeroTierClient = ZeroTierClient(it) }
    }

    private fun getNetworkName(): String {
        return "root:${authority.entityId}"
    }

    private fun getNodeId(): String {
        return node.id.toString(16)
    }

    private fun joinNetwork(address: String){
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.join(id)
    }

    private fun leaveNetwork(address: String) {
        val id = LongByteArrayCodec.decode(address.hexStringToByteArray())
        node.leave(id)
    }

    private fun authorityToMember(authority: Authority): Member {
        return Member.forCreate(
            authority.name ?: authority.entityId.toString(),
            MemberConfig.forCreate(authorized = true),
            hidden = false,
            description = "OpenCola Id: ${authority.entityId}"
        )
    }

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
            "Root network for: ${authority.name} (${authority.entityId})"
        )
        val createdNetwork = zeroTierClient.createNetwork(network)

        if(createdNetwork.id == null){
            throw RuntimeException("Unable to create root network")
        }

        zeroTierClient.addNetworkMember(createdNetwork.id, getNodeId(), authorityToMember(authority))

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

    override fun start() {
        logger.info { "Starting..." }
        node.initFromStorage(storagePath.toString())
        node.initSetEventHandler(OCZeroTierEventListener())
        node.start()

        while (!node.isOnline) {
            // TODO: Break infinite loop
            ZeroTierNative.zts_util_delay(50);
        }

        networkId = getOrCreateNodeNetwork()?.id
        logger.info { "Started: ${getNodeId()}" }
    }

    override fun stop() {
        logger.info { "Stopping..." }
        node.stop()
        logger.info { "Stopped: ${getNodeId()}" }
    }

    override fun getAddress(): URI {
        return ZeroTierAddress(networkId, getNodeId()).toURI()
    }

    override fun updatePeer(peer: Authority) {
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
        val networkId = this.networkId
        if (zeroTierClient != null) {
            // Allow the peer node onto our network
            networkId ?: throw IllegalArgumentException("Unable to determine local network id to add peer to. Check peer settings")

            logger.info { "Adding peer to local node network" }
            zeroTierClient.addNetworkMember(networkId, zeroTierAddress.nodeId, authorityToMember(peer))
        }
    }

    override fun removePeer(peer: Authority) {
        TODO("Not yet implemented")
    }
}