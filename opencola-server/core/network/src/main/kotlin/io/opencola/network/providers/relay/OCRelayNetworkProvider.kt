package io.opencola.network.providers.relay

import io.opencola.model.Id
import io.opencola.network.NetworkConfig
import io.opencola.network.AbstractNetworkProvider
import io.opencola.network.message.SignedMessage
import io.opencola.security.Encryptor
import io.opencola.security.Signator
import io.opencola.storage.addressbook.AddressBook
import io.opencola.relay.client.RelayClient
import io.opencola.relay.client.WebSocketClient
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

const val openColaRelayScheme = "ocr"

class OCRelayNetworkProvider(
    addressBook: AddressBook,
    signator: Signator,
    encryptor: Encryptor,
    private val networkConfig: NetworkConfig,
) : AbstractNetworkProvider(addressBook, signator, encryptor) {
    private val logger = KotlinLogging.logger("OCRelayNetworkProvider")

    private data class ConnectionInfo(val client: RelayClient, val listenThread: Thread)
    private data class ConnectionParams(val uri: URI, val keyPair: KeyPair) {
        override fun toString(): String {
            return "ConnectionParams(uri=$uri, keyPair=${Id.ofPublicKey(keyPair.public)})"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ConnectionParams) return false

            if (uri != other.uri) return false
            if(!keyPair.public.encoded.contentEquals(other.keyPair.public.encoded)) return false
            if(!keyPair.private.encoded.contentEquals(other.keyPair.private.encoded)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = uri.hashCode()
            result = 31 * result + keyPair.public.hashCode()
            result = 31 * result + keyPair.private.hashCode()
            return result
        }
    }

    private val connections = ConcurrentHashMap<ConnectionParams, ConnectionInfo>()

    @Synchronized
    private fun addClient(connectionParams: ConnectionParams): RelayClient {
        val uri = connectionParams.uri
        if (uri.scheme != openColaRelayScheme) {
            throw IllegalArgumentException("$uri does not match $openColaRelayScheme")
        }

        connections[connectionParams]?.let {
            return it.client
        }

        val client =
            WebSocketClient(uri, connectionParams.keyPair, uri.toString(), networkConfig.requestTimeoutMilliseconds)
        // TODO: Move away from threads, or at least just use one
        val listenThread = thread {
            try {
                runBlocking {
                    logger.info { "Opening client: $connectionParams" }
                    client.open { _, request ->
                        try {
                            handleMessage(request, false)
                        } catch (e: Throwable) {
                            logger.error { "Error handling message: $e" }
                        }
                    }
                }
            } catch (e: InterruptedException) {
                // Expected on shutdown
            }
        }

        // TODO: The underlying client may reconnect due to server partitioning, sleep/wake, etc. When this happens
        //  it might be good to request new transactions from any peers on that connection
        connections[connectionParams] = ConnectionInfo(client, listenThread)

        return client
    }

    override fun start(waitUntilReady: Boolean) {
        val (personaAuthorities, peerAuthorities) = addressBook.getEntries().partition { it is PersonaAddressBookEntry }
        val personas = personaAuthorities.associate { it.entityId to it as PersonaAddressBookEntry }

        peerAuthorities
            .filter { it.isActive }
            .filter { it.address.scheme == openColaRelayScheme }
            // TODO: Test that only distinct ConnectionParams are added
            .mapNotNull { peer -> personas[peer.personaId]?.let { ConnectionParams(peer.address, it.keyPair) } }
            .distinct()
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
            connections.clear()
        }
        started = false
        logger.info { "Stopped" }
    }

    override fun getScheme(): String {
        return openColaRelayScheme
    }

    override fun validateAddress(address: URI) {
        if (address.scheme != getScheme()) {
            throw IllegalArgumentException("Invalid scheme for provider: ${address.scheme}")
        }

        // Check that connection can be established
        try {
            val keyPair = addressBook.getEntries().filterIsInstance<PersonaAddressBookEntry>().first().keyPair

            runBlocking {
                WebSocketClient(
                    address,
                    keyPair,
                    requestTimeoutMilliseconds = networkConfig.requestTimeoutMilliseconds
                ).getSocketSession().close()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Could not establish connection to relay server ($address): ${e.message}")
        }
    }

    override fun addPeer(peer: AddressBookEntry) {
        logger.info { "Adding peer: $peer" }
        val persona = addressBook.getEntry(peer.personaId, peer.personaId) as? PersonaAddressBookEntry
            ?: throw IllegalStateException("Can't add peer ${peer.entityId} for unknown persona: ${peer.personaId}")

         addClient(ConnectionParams(peer.address, persona.keyPair))
    }

    override fun removePeer(peer: AddressBookEntry) {
        val persona = addressBook.getEntry(peer.personaId, peer.personaId) as? PersonaAddressBookEntry
            ?: throw IllegalArgumentException("Can't remove peer ${peer.entityId} for unknown persona: ${peer.personaId}")

        logger.info { "Removing peer: ${peer.entityId}" }
        if (addressBook.getEntries()
                .filter { it.isActive }
                .none { it.address == peer.address && it.personaId == persona.entityId && it.entityId != peer.entityId }
        ) {
            val connectionParams = ConnectionParams(peer.address, persona.keyPair)
            runBlocking { connections[connectionParams]?.client?.close() }
            connections.remove(connectionParams)
        }
    }

    // TODO: Since to Authority has a persona associated with it, do we need the from Authority?
    // TODO: Should from and to be entries of ids?
    override fun sendMessage(from: PersonaAddressBookEntry, to: AddressBookEntry, signedMessage: SignedMessage) {
        val peerUri = to.address

        if (peerUri.scheme != openColaRelayScheme) {
            logger.warn { "Unexpected uri scheme in sendRequest: $peerUri" }
        }

        val peerPublicKey = to.publicKey

        // TODO: Seems weird that we look this up again - from is passed in. This does block from sending to a peer
        //  not in the address book. Maybe ids should be passed in.
        val connectionParams = (addressBook.getEntry(from.personaId, from.entityId) as? PersonaAddressBookEntry)?.let {
            ConnectionParams(
                peerUri,
                it.keyPair
            )
        }
            ?: throw IllegalStateException("Can't send request from unknown persona: ${from.personaId}")

        if (connections[connectionParams] == null) {
            logger.warn { "Connection info missing for: $to" }
            addClient(connectionParams)
        }

        logger.info { "Sending request from: ${from.name} to: ${to.name} request: $signedMessage" }

        runBlocking {
            try {
                val envelopeBytes = getEncodedEnvelope(from.entityId, to.entityId, signedMessage, false)
                val client = connections[connectionParams]!!.client
                client.sendMessage(peerPublicKey, envelopeBytes)
            } catch (e: Exception) {
                logger.error { "sendMessage: $e" }
            }
        }
    }
}