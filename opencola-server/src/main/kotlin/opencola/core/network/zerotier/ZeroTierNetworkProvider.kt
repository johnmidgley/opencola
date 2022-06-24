package opencola.core.network.zerotier

import com.zerotier.sockets.*
import mu.KotlinLogging
import opencola.core.config.ZeroTierConfig
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.shutdownWithTimout
import opencola.core.model.Authority
import opencola.core.network.NetworkProvider
import opencola.core.serialization.LongByteArrayCodec
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.math.log


class ZeroTierNetworkProvider(
    val storagePath: Path,
    val config: ZeroTierConfig,
    val authority: Authority,
) : NetworkProvider {
    private val logger = KotlinLogging.logger("ZeroTierNetworkProvider")
    private val node = ZeroTierNode()
    private var networkId: String? = null
    private var authToken: String? = null
    private var zeroTierClient: ZeroTierClient? = null
    private val executorService = Executors.newFixedThreadPool(5)
    private var started: Boolean = false

    init {
        if(!config.providerEnabled)
            throw IllegalStateException("Attempt to instantiate ZeroTierNetworkProvider when not enabled in config")
    }

    private fun getNodeId(): String {
        return node.id.toString(16)
    }

    private fun getNetworkName(): String {
        return "root:${authority.entityId}"
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
        val zeroTierClient = zeroTierClient ?: throw IllegalStateException("Can't create network without zeroTierClient")
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
        networkId = createdNetwork.id

        return createdNetwork
    }

    private fun getOrCreateNodeNetwork() : Network? {
        val zeroTierClient = zeroTierClient ?: return null

        val networkName = getNetworkName()
        val networks = zeroTierClient.getNetworks().filter { it.config?.name == networkName }

        if(networks.size > 1) {
            throw IllegalStateException("Multiple networks exist with the root name: $networkName. Fix at ZT Central")
        }

        return networks.singleOrNull() ?: createNodeNetwork()
    }

    private fun handleConnection(socket: ZeroTierSocket) {
        val inputStream: ZeroTierInputStream = socket.inputStream
        val dataInputStream = DataInputStream(inputStream)
        val message = dataInputStream.readUTF()
        println("RX: $message")
        socket.close()
    }

    private fun listenForConnections() {
        logger.info { "Listening for connections" }
        val listener = ZeroTierServerSocket(config.port)

        while(started){
            handleConnection(listener.accept())
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
        started = true
        // executorService.execute { listenForConnections() }
        logger.info { "Started: ${node.id.toString(16)}" }
    }

    override fun stop() {
        logger.info { "Stopping ${node.id.toString(16)}..." }
        started = false
        node.stop()
        executorService.shutdownWithTimout(1000)
        logger.info { "Stopped" }
    }

    override fun getAddress(): URI {
        return ZeroTierAddress(networkId, getNodeId(), config.port).toURI()
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

    override fun setNetworkToken(token: String?) {
        if (authToken != token) {
            authToken = token
            zeroTierClient = token.nullOrElse { ZeroTierClient(it) }
            getOrCreateNodeNetwork()
        }
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
            LongByteArrayCodec.decode(it.hexStringToByteArray())
        }

        if(nodeId == null){
            logger.error { "Unable to determine nodeId to send message: $zeroTierAddress" }
            return
        }

        val address = node.getIPv6Address(nodeId).toString()
        // TODO: Add port to zeroTierAddress
        val socket = ZeroTierSocket(address, config.port)
        val outputStream = socket.outputStream;
        val dataOutputStream = DataOutputStream(outputStream);
        dataOutputStream.writeUTF(message);
        socket.close()
    }
}