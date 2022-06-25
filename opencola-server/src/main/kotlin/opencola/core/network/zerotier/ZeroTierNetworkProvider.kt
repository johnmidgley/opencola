package opencola.core.network.zerotier

import com.zerotier.sockets.*
import mu.KotlinLogging
import opencola.core.config.ZeroTierConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.shutdownWithTimout
import opencola.core.model.Authority
import opencola.core.network.NetworkProvider
import opencola.core.serialization.LongByteArrayCodec
import java.io.DataInputStream
import java.io.DataOutputStream
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
    private var networkId: String? = null
    private var zeroTierClient: ZeroTierClient? = ZeroTierClient(authToken)
    private val executorService = Executors.newFixedThreadPool(5)
    private var running: Boolean = false

    init {
        if(!config.providerEnabled)
            throw IllegalStateException("Attempt to instantiate ZeroTierNetworkProvider when not enabled in config")
    }

    // TODO: Clean up Ids = when strings vs. Longs
    private fun getNodeId(): String {
        return node.id.toString(16)
    }

    private fun getNetworkName(): String {
        return "root:${authority.entityId}"
    }

    private fun stringIdToLongId(id: String) : Long {
        return LongByteArrayCodec.decode(id.hexStringToByteArray())
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

        return (networks.singleOrNull() ?: createNodeNetwork()).also { networkId = it.id }
    }

    private fun handleConnection(socket: ZeroTierSocket) {
        logger.info { "Socket Connected!" }
        val inputStream: ZeroTierInputStream = socket.inputStream
        val dataInputStream = DataInputStream(inputStream)
        val message = dataInputStream.readUTF()
        logger.info("RX: $message")
        socket.close()
    }

    private fun waitUntilNetworkReady() {
        logger.info { "Waiting for network $networkId to be ready"  }
        val networkId = networkId?.let { stringIdToLongId(it) }
            ?: throw java.lang.IllegalStateException("No network available. A ZT networkToken must be set")

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
        logger.info { "Listening for connections - port: ${config.port}" }

        val listener = ZeroTierServerSocket(config.port)

        while(running) {
            try {
                handleConnection(listener.accept())
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
        return ZeroTierAddress(networkId, getNodeId(), config.port)
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
                zeroTierClient.addNetworkMember(networkId, zeroTierAddress.nodeId, authorityToMember(peer))
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
                zeroTierClient.deleteNetworkMember(networkId, zeroTierAddress.nodeId)
            else
                logger.warn { "Local network does not exist - unable to remove peer" }
        }
    }

    fun sendMessage(peer: Authority, message: String) {
        val zeroTierAddress = peer.uri?.let { ZeroTierAddress.fromURI(it) }

        if(zeroTierAddress == null) {
            logger.error { "No ZT address to send message to: uri: ${authority.uri}" }
            return
        }

        val nodeId = zeroTierAddress.nodeId?.let{
            it.toLong(16)
            // LongByteArrayCodec.decode(it.hexStringToByteArray())
        }

        if(nodeId == null){
            logger.error { "Unable to determine nodeId to send message: $zeroTierAddress" }
            return
        }

        if(zeroTierAddress.port == null){
            logger.error { "No port specified in peer address" }
            return
        }

        zeroTierAddress.port
        val address = node.getIPv4Address(nodeId).hostAddress
        val socket = ZeroTierSocket(address, zeroTierAddress.port)
        val outputStream = socket.outputStream;
        val dataOutputStream = DataOutputStream(outputStream);
        dataOutputStream.writeUTF(message);
        socket.close()
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