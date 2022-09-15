package io.opencola.core.network.providers.relay

import io.opencola.core.model.Authority
import io.opencola.core.network.AbstractNetworkProvider
import io.opencola.core.network.Request
import io.opencola.core.network.Response
import io.opencola.core.storage.AddressBook
import io.opencola.relay.client.Client
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

class OCRelayNetworkProvider(private val addressBook: AddressBook, private val keyPair: KeyPair): AbstractNetworkProvider() {
    private val logger = KotlinLogging.logger("OCRelayNetworkProvider")
    data class ConnectionInfo(val client: Client, val listenThread: Thread)
    private val connections = ConcurrentHashMap<URI, ConnectionInfo>()
    // TODO: Move to AbstractProvider
    var started = false

    private fun addClient(uri: URI) {
        if(uri.scheme == openColaRelayScheme && !connections.contains(uri)) {
            val client = WebSocketClient(uri.host, uri.port, keyPair, uri.toString())
            val listenThread = thread {
                runBlocking {
                    client.open { request -> handleRequest(request) }
                }
            }
            connections[uri] = ConnectionInfo(client, listenThread)
        }
    }

    override fun start() {
        addressBook
            .getAuthorities(true)
            .filter {  it.uri?.scheme == openColaRelayScheme }
            .mapNotNull { it.uri }
            .toSet()
            .forEach{ addClient(it) }
        started = true
    }

    override fun stop() {
        runBlocking {
            connections.values.forEach() {
                it.client.close()
                it.listenThread.interrupt()
            }
        }
        started = false
    }

    override fun getAddress(): URI {
        TODO("Not yet implemented")
    }

    override fun addPeer(peer: Authority) {
        peer.uri?.let { addClient(it) }
    }

    override fun removePeer(peer: Authority) {
        peer.uri?.let { peerUri ->
            if (addressBook.getAuthorities(true).none { it.uri == peerUri && it.entityId != peer.entityId })
                connections.remove(peerUri)
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

    private fun handleRequest(bytes: ByteArray) : ByteArray {
        if (!started) throw IllegalStateException("Provider is not started - can't handleRequest")
        val handler = this.handler ?: throw IllegalStateException("Call to handleRequest when handler has not been set")
        val request = Json.decodeFromString<Request>(String(bytes))
        val response = handler(request)

        return Json.encodeToString(response).toByteArray()
    }
}