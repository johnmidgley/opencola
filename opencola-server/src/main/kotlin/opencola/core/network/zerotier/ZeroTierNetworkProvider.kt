package opencola.core.network.zerotier

import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierNode
import com.zerotier.sockets.ZeroTierServerSocket
import com.zerotier.sockets.ZeroTierSocket
import mu.KotlinLogging
import opencola.core.config.ZeroTierConfig
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.shutdownWithTimout
import opencola.core.model.Authority
import opencola.core.network.NetworkProvider
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.URI
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executors


class ZeroTierNetworkProvider(
    private val storagePath: Path,
    private val config: ZeroTierConfig,
    private val authority: Authority,
    authToken: String
) : NetworkProvider {
    private val node = ZeroTierNode()
    private var networkId: ZeroTierId? = null
    private var zeroTierClient: ZeroTierClient? = ZeroTierClient(authToken)
    private val executorService = Executors.newFixedThreadPool(5) // TODO: Config
    private var running: Boolean = false

    init {
        if(!config.providerEnabled)
            throw IllegalStateException("Attempt to instantiate ZeroTierNetworkProvider when not enabled in config")
    }

    // TODO: Clean up Ids = when strings vs. Longs
    private fun getNodeId(): String {
        return node.id.toString(16)
    }

    private fun expectNetworkId() : ZeroTierId {
        return networkId
            ?: throw IllegalStateException("Network id not present when expected. Is a ZeroTier auth token present in root peer settings?")
    }

    private fun getNetworkName(): String {
        return "root:${authority.entityId}"
    }

    private fun stringIdToLongId(id: String) : Long {
        return BigInteger(id, 16).toLong()
        // return LongByteArrayCodec.decode(id.hexStringToByteArray())
    }

    private fun joinNetwork(id: String){
        node.join(stringIdToLongId(id))
    }

    private fun leaveNetwork(id: String) {
        node.leave(stringIdToLongId(id))
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
        val zeroTierClient = zeroTierClient ?: throw IllegalStateException("Can't create network without zeroTierClient")
        val networkConfig = NetworkConfig.forCreate(
            name = getNetworkName(),
            private = true,
            // TODO: Figure out how this should be set. Just use ipv6? Is it even needed?
            routes = listOf(Route("10.0.0.0/8")),
            v4AssignMode = IPV4AssignMode(true),
            ipAssignmentPools = listOf(IPRange("10.0.0.0", "10.255.255.255"))
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
        joinNetwork(createdNetwork.id)

        return createdNetwork
    }

    private fun getOrCreateNodeNetwork() : Network? {
        val zeroTierClient = zeroTierClient ?: return null

        val networkName = getNetworkName()
        val networks = zeroTierClient.getNetworks().filter { it.config?.name == networkName }

        if(networks.size > 1) {
            throw IllegalStateException("Multiple networks exist with the root name: $networkName. Fix at ZT Central")
        }

        val network = networks.singleOrNull() ?: createNodeNetwork()

        if(network.id == null)
            throw RuntimeException("Unable to determine networkId")

        val networkId = ZeroTierId(network.id).also { this.networkId = it }

        networkId.nullOrElse {
            while (!node.isNetworkTransportReady(networkId.toLong())) {
                ZeroTierNative.zts_util_delay(50)
            }
        }

        return network
    }

    private fun handleConnection(socket: ZeroTierSocket) {
        try {
            logger.info { "Socket Connected" }
            DataInputStream(socket.inputStream).use { logger.info("Received: ${it.readUTF()}") }
            DataOutputStream(socket.outputStream).use { it.writeUTF("OK") }
        } finally {
            socket.close()
        }
    }

    private fun waitUntilNetworkReady() {
        logger.info { "Waiting for network $networkId to be ready"  }
        val networkId = expectNetworkId().toLong()
        val startTime = Instant.now().epochSecond

        // TODO: Config timeout
        while (!node.isNetworkTransportReady(networkId) && Instant.now().epochSecond - startTime < 10) {
            ZeroTierNative.zts_util_delay(50);
        }

        if(running && !node.isNetworkTransportReady(networkId)){
            throw RuntimeException("Transport layer timed out")
        }
    }

    private fun listenForConnections() {
        waitUntilNetworkReady()
        val address = node.getIPv4Address(expectNetworkId().toLong());
        logger.info { "Listening for connections - $address port: ${config.port}" }
        val listener = ZeroTierServerSocket(config.port)

        while(running) {
            try {
                listener.accept().also { executorService.execute { handleConnection(it) } }
            } catch (e: Exception) {
                logger.error { "Unhandled exception while listening for connections: $e" }
            }
        }

        listener.close()
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

        getOrCreateNodeNetwork()
        val ip4Address = node.getIPv4Address(expectNetworkId().toLong())
        logger.info { "Network started with ip: ${ip4Address.hostAddress}"}
        running = true
        executorService.execute { listenForConnections() }
        logger.info { "Started: ${node.id.toString(16)}" }
    }

    override fun stop() {
        logger.info { "Stopping ${node.id.toString(16)}..." }
        running = false
        node.stop()
        executorService.shutdownWithTimout(1000)
        logger.info { "Stopped" }
    }

    private fun getZeroTierAddress() : ZeroTierAddress {
        return ZeroTierAddress(expectNetworkId().toString(), getNodeId(), config.port)
    }

    override fun getAddress(): URI {
        return getZeroTierAddress().toURI()
    }

    override fun updatePeer(peer: Authority) {
        if(peer.entityId != authority.entityId) {
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

            val zeroTierClient = zeroTierClient
            val networkId = networkId
            if (zeroTierClient != null && networkId != null) {
                // Allow the peer node onto our network
                logger.info { "Adding peer to local node network: ${authority.name}" }
                zeroTierClient.addNetworkMember(expectNetworkId().toString(), zeroTierAddress.nodeId, authorityToMember(peer))
            } else
                logger.warn { "Local network does not exist - unable to add peer" }
        }
    }

    // TODO: Test
    override fun removePeer(peer: Authority) {
        val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) } ?: return
        if (zeroTierAddress.networkId != null) {
            // When we connected, we joined the peer's network, so we need to now leave it
            leaveNetwork(zeroTierAddress.networkId)
        } else if (zeroTierAddress.nodeId != null) {
            // Remove the peer from our network
            val zeroTierClient = zeroTierClient
            val networkId = networkId

            if(zeroTierClient != null && networkId != null)
                zeroTierClient.deleteNetworkMember(expectNetworkId().toString(), zeroTierAddress.nodeId)
            else
                logger.warn { "Local network does not exist - unable to remove peer" }
        }
    }

    private fun getRemoteAddress(zeroTierAddress: ZeroTierAddress) : String? {
        val zeroTierClient = this.zeroTierClient

        if(zeroTierClient == null || networkId == null) {
            logger.error { "zeroTierClient AND networkId not set" }
            return null
        }

        val nodeId = zeroTierAddress.nodeId?.let{
            // TODO: Move text <-> long conversions to ZeroTierAddress
            it.toLong(16)
            // LongByteArrayCodec.decode(it.hexStringToByteArray())
        }

        if(nodeId == null){
            logger.error { "Unable to determine nodeId to send message: $zeroTierAddress" }
            return null
        }

        if(zeroTierAddress.port == null){
            logger.error { "No port specified in peer address" }
            return null
        }

        return node.getIPv4Address(nodeId).hostAddress.let {
            if(it == "127.0.0.1")
                zeroTierClient
                    .getNetworkMember(expectNetworkId().toString(), zeroTierAddress.nodeId)
                    .config
                    ?.ipAssignments
                    ?.firstOrNull()
            else
                it
        }
    }

    fun sendMessage(peer: Authority, message: String) {
        logger.info { "Sending message: ${authority.name} - $message" }

        val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) }

        if(zeroTierAddress == null) {
            logger.error { "No ZT address to send message to: uri: ${authority.uri}" }
            return
        }

        if(zeroTierAddress.port == null) {
            logger.error { "No port specified in zeroTierAddress: $zeroTierAddress" }
            return
        }

        val address = getRemoteAddress(zeroTierAddress) ?: return
        logger.info { "Remote Address: $address" }
        val socket = ZeroTierSocket(address, zeroTierAddress.port)

        try {
            DataOutputStream(socket.outputStream).use { it.writeUTF(message) }
            DataInputStream(socket.inputStream).use { logger.info { "Response: ${it.readUTF()}" } }
        } finally {
            socket.close()
        }
    }

    companion object Factory {
        private val logger = KotlinLogging.logger("ZeroTierNetworkProvider")

        fun isNetworkTokenValid(token: String): Boolean {
            try {
                ZeroTierClient(token).getNetworks()
            } catch (e: Exception) {
                logger.debug { e }
                return false
            }
            return true
        }
    }
}