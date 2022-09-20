package io.opencola.core.network.providers.relay

import io.opencola.core.model.Authority
import io.opencola.core.network.AbstractNetworkProvider
import io.opencola.core.network.Request
import io.opencola.core.network.Response
import io.opencola.core.storage.AddressBook
import io.opencola.relay.client.RelayClient
import io.opencola.relay.client.WebSocketClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

const val openColaRelayScheme = "ocr"

class OCRelayNetworkProvider(private val addressBook: AddressBook, private val keyPair: KeyPair): AbstractNetworkProvider() {
    private val logger = KotlinLogging.logger("OCRelayNetworkProvider")
    data class ConnectionInfo(val client: RelayClient, val listenThread: Thread)
    private val connections = ConcurrentHashMap<URI, ConnectionInfo>()
    // TODO: Move to AbstractProvider
    var started = false

    @Synchronized
    private fun addClient(uri: URI): RelayClient {
        if(uri.scheme != openColaRelayScheme) {
            throw IllegalArgumentException("$uri does not match $openColaRelayScheme")
        }

        connections[uri]?.let {
            logger.warn { "Client already started: $uri" }
            return it.client
        }

        val client = WebSocketClient(uri, keyPair, uri.toString())
        // TODO: Move away from threads, or at least just use one
        val listenThread = thread {
            try {
                runBlocking {
                    logger.info { "Opening client: $uri" }
                    client.open { publicKey, request -> handleRequest(publicKey, request) }
                }
            } catch (e: InterruptedException) {
                // Expected on shutdown
            }
        }

        // TODO: The underlying client may reconnect due to server partitioning, sleep/wake, etc. When this happens
        //  it might be good to request new transactions from any peers on that connection
        connections[uri] = ConnectionInfo(client, listenThread)

        return client
    }

    override fun start() {
        addressBook
            .getAuthorities(true)
            .filter { it.uri?.scheme == openColaRelayScheme }
            .mapNotNull { it.uri }
            .toSet()
            .forEach{ addClient(it) }
        started = true
        logger.info { "Started" }
    }

    override fun stop() {
        runBlocking {
            connections.values.forEach() {
                it.client.close()
                it.listenThread.interrupt()
            }
        }
        started = false
        logger.info { "Stopped" }
    }

    override fun getScheme(): String {
        return openColaRelayScheme
    }

    override fun validateAddress(address: URI) {
        if(address.scheme != getScheme()) {
            throw IllegalArgumentException("Invalid scheme for provider: ${address.scheme}")
        }

        // Check that connection can be established
        try {
            runBlocking { WebSocketClient(address, keyPair).getSocketSession().close() }
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not establish connection to relay server ($address): ${e.message}")
        }
    }

    override fun addPeer(peer: Authority) {
        peer.uri?.let { addClient(it) }
    }

    override fun removePeer(peer: Authority) {
        logger.info { "Removing peer: ${peer.entityId}" }
        peer.uri?.let { peerUri ->
            if (addressBook.getAuthorities(true).none { it.uri == peerUri && it.entityId != peer.entityId }) {
                runBlocking { connections[peerUri]?.client?.close() }
                connections.remove(peerUri)
            }
        }
    }

    override fun sendRequest(peer: Authority, request: Request): Response? {
        val peerUri = peer.uri ?: return null

        if(peerUri.scheme != openColaRelayScheme) {
            logger.warn { "Unexpected uri scheme in sendRequest: $peerUri" }
        }

        val peerPublicKey = peer.publicKey
        if(peerPublicKey == null) {
            logger.warn { "Can't send message to peer with no public key specified: ${peer.entityId}" }
            return null
        }

        if(connections[peerUri] == null) {
            logger.warn { "Connection info missing for: $peerUri" }
            addClient(peerUri)
        }

        return runBlocking {
            connections[peerUri]!!.client.sendMessage(peerPublicKey, Json.encodeToString(request).toByteArray())?.let {
                Json.decodeFromString<Response>(String(it))
            }
        }
    }

    private fun handleRequest(fromPublicKey: PublicKey, bytes: ByteArray): ByteArray {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        val request = Json.decodeFromString<Request>(String(bytes))
        val fromAuthority = addressBook.getAuthority(request.from)

        val response = if (fromAuthority == null) {
            logger.warn { "Received request from unknown authority: ${request.from}" }
            Response(401, "Unknown sender")
        } else if (fromAuthority.publicKey != fromPublicKey) {
            val message = "Request public key does not match authority public key"
            logger.error { message }
            Response(400, message)
        } else
            handler(request)

        return Json.encodeToString(response).toByteArray()
    }
}