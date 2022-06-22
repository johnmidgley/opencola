package opencola.core.network.zerotier

import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierNode
import mu.KotlinLogging
import opencola.core.model.Authority
import opencola.core.network.NetworkProvider
import opencola.core.storage.AddressBook
import java.net.URI
import java.nio.file.Path

class ZeroTierNetworkProvider(val storagePath: Path, val addressBook: AddressBook) : NetworkProvider {
    private val logger = KotlinLogging.logger("ZeroTierNetworkProvider")
    val node = ZeroTierNode()
    var networkId: String? = null
    var authority: Authority? = null
    var authToken: String? = null
    var zeroTierClient: ZeroTierClient? = null

    private fun getNodeId(): String {
        return node.id.toString(16)
    }

    private fun getNetworkName(): String {
        return "root:${authority!!.entityId}"
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

    fun initNodeNetwork() {
        val networkId = getOrCreateNodeNetwork()?.id
        val authority = authority!!
        authority.uri = ZeroTierAddress(networkId, getNodeId()).toURI()
        addressBook.updateAuthority(authority)
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

        // initNodeNetwork()
        logger.info { "Started: ${node.id.toString(16)}" }
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun getAddress(): URI {
        return ZeroTierAddress(networkId, getNodeId()).toURI()
    }

    override fun isNetworkTokenValid(token: String): Boolean {
        try {
            ZeroTierClient(token).getNetworks()
        } catch (e: Exception) {
            logger.debug { e }
            return false
        }

        return true
    }

    override fun setNetworkToken(token: String) {
        if (authToken != token) {
            authToken = token
            zeroTierClient = ZeroTierClient(token)
            networkId = getOrCreateNodeNetwork()?.id
        }
    }

    override fun updatePeer(peer: Authority) {
        TODO("Not yet implemented")
    }

    override fun removePeer(peer: Authority) {
        TODO("Not yet implemented")
    }
}