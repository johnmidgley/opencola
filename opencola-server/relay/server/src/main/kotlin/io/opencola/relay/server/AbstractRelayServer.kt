package io.opencola.relay.server

import io.opencola.model.Id
import io.opencola.security.initProvider
import io.opencola.relay.common.connection.Connection
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.State.*
import io.opencola.relay.common.message.store.MemoryMessageStore
import io.opencola.relay.common.message.store.MessageStore
import io.opencola.relay.common.message.store.Usage
import io.opencola.security.generateKeyPair
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractRelayServer(
    protected val numChallengeBytes: Int = 32,
    private val messageStore: MessageStore? = null
) {
    private val connections = ConcurrentHashMap<PublicKey, Connection>()
    protected val keyPair = generateKeyPair()
    protected val logger = KotlinLogging.logger("RelayServer")
    protected val openMutex = Mutex(true)
    protected val random = SecureRandom()
    protected var state = Initialized
    protected var listenJob: Job? = null

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    suspend fun connectionStates() : List<Pair<String, Boolean>> {
        return connections.map { Pair(Id.ofPublicKey(it.key).toString(), it.value.isReady()) }
    }

    fun getUsage() : Sequence<Usage> {
       return (messageStore as? MemoryMessageStore)?.getUsage() ?: emptySequence()
    }

    protected abstract suspend fun authenticate(socketSession: SocketSession): PublicKey?
    protected abstract fun decodePayload(payload: ByteArray): Envelope

    suspend fun handleSession(socketSession: SocketSession) {
        authenticate(socketSession)?.let { publicKey ->
            val connection = Connection(socketSession, Id.ofPublicKey(publicKey).toString())
            logger.info { "Session authenticated for: ${connection.name}" }
            connections[publicKey] = connection

            try {
                // Send any stored messages
                messageStore?.getMessages(publicKey)?.forEach {
                    connection.writeSizedByteArray(it.envelope.message)
                    messageStore.removeMessage(it)
                }

                // TODO: Add garbage collection on inactive connections?
                connection.listen { payload -> handleMessage(publicKey, payload) }
            } finally {
                connection.close()
                connections.remove(publicKey)
                logger.info { "Session closed for: ${connection.name}" }
            }
        }
    }

    abstract suspend fun open()

    private suspend fun handleMessage(from: PublicKey, payload: ByteArray) {
        val fromId = Id.ofPublicKey(from)
        var messageDelivered = false
        var envelope: Envelope? = null

        try {
            envelope = decodePayload(payload)
            val toId = Id.ofPublicKey(envelope.to)
            val prefix = "from=$fromId, to=$toId:"

            if (from != envelope.to) {
                val connection = connections[envelope.to]

                if (connection == null) {
                    logger.info { "$prefix no connection to receiver" }
                } else if (!connection.isReady()) {
                    logger.info { "$prefix Removing closed connection for receiver" }
                } else {
                    logger.info { "$prefix Delivering ${envelope.message.size} bytes" }
                    connection.writeSizedByteArray(envelope.message)
                    messageDelivered = true
                }
            }
        } catch (e: Exception) {
            logger.error { "Error while handling message from $fromId: $e" }
        } finally {
            try {
                if (!messageDelivered && envelope?.key != null)
                    messageStore?.addMessage(from, envelope)
            } catch (e: Exception) {
                logger.error { "Error while storing message $envelope: $e" }
            }
        }
    }

    open suspend fun close() {
        state = Closed
        connections.values.forEach { it.close() }
        connections.clear()
        listenJob?.cancel()
        listenJob = null
    }

    companion object {
        init {
            initProvider()
        }
    }
}