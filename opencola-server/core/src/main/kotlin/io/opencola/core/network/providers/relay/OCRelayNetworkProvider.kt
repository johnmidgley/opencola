package io.opencola.core.network.providers.relay

import io.opencola.core.config.NetworkConfig
import io.opencola.core.model.Authority
import io.opencola.core.network.*
import io.opencola.core.security.Encryptor
import io.opencola.core.security.Signator
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

const val openColaRelayScheme = "ocr"

class OCRelayNetworkProvider(authority: Authority,
                             addressBook: AddressBook,
                             signator: Signator,
                             encryptor: Encryptor,
                             private val keyPair: KeyPair, // Seems redundant, but needed for Relay client.
                             private val networkConfig: NetworkConfig,
): AbstractNetworkProvider(authority, addressBook, signator, encryptor) {
    private val logger = KotlinLogging.logger("OCRelayNetworkProvider")
    data class ConnectionInfo(val client: RelayClient, val listenThread: Thread)
    private val connections = ConcurrentHashMap<URI, ConnectionInfo>()

    @Synchronized
    private fun addClient(uri: URI): RelayClient {
        if(uri.scheme != openColaRelayScheme) {
            throw IllegalArgumentException("$uri does not match $openColaRelayScheme")
        }

        connections[uri]?.let {
            return it.client
        }

        val client = WebSocketClient(uri, keyPair, uri.toString(), networkConfig.requestTimeoutMilliseconds)
        // TODO: Move away from threads, or at least just use one
        val listenThread = thread {
            try {
                runBlocking {
                    logger.info { "Opening client: $uri" }
                    client.open { _, request -> handleMessage(request, false) }
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

    override fun start(waitUntilReady: Boolean) {
        addressBook
            .getAuthorities(true)
            .filter { it.uri?.scheme == openColaRelayScheme }
            .mapNotNull { it.uri }
            .toSet()
            .forEach {
                addClient(it).also {
                    if (waitUntilReady) {
                        runBlocking {
                            it.waitUntilOpen()
                        }
                    }
                }
            }
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
            runBlocking {
                WebSocketClient(
                    address,
                    keyPair,
                    requestTimeoutMilliseconds = networkConfig.requestTimeoutMilliseconds).getSocketSession().close()
            }
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

    override fun sendRequest(from: Authority, to: Authority, request: Request): Response? {
        val peerUri = to.uri ?: return null

        if(peerUri.scheme != openColaRelayScheme) {
            logger.warn { "Unexpected uri scheme in sendRequest: $peerUri" }
        }

        val peerPublicKey = to.publicKey
        if(peerPublicKey == null) {
            logger.warn { "Can't send message to peer with no public key specified: ${to.entityId}" }
            return null
        }

        if(connections[peerUri] == null) {
            logger.warn { "Connection info missing for: $peerUri" }
            addClient(peerUri)
        }

        return runBlocking {
            try {
                val messageBytes = Json.encodeToString(request).toByteArray()
                val envelopeBytes = getEncodedEnvelope(from.entityId, to.entityId, messageBytes, false)
                val client = connections[peerUri]!!.client
                client.sendMessage(peerPublicKey, envelopeBytes)?.let {
                    // We don't need to validate sender - OC relay enforces that response is from correct sender
                    val responseEnvelope = MessageEnvelope.decode(it).also { e -> validateMessageEnvelope(e) }
                    Json.decodeFromString<Response>(String(responseEnvelope.message.body))
                }
            } catch (e: Exception) {
                logger.error { "sendRequest: $e" }
                null
            }

        }
    }
}