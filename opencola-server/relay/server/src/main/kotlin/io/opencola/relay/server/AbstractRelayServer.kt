package io.opencola.relay.server

import io.opencola.model.Id
import io.opencola.relay.common.connection.Connection
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.State.*
import io.opencola.relay.common.message.*
import io.opencola.relay.common.message.v2.ControlMessage
import io.opencola.relay.common.message.v2.ControlMessageType
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractRelayServer(
    protected val numChallengeBytes: Int = 32,
    protected val numSymmetricKeyBytes: Int = 32,
    private val messageStore: MessageStore? = null
) {
    private val connections = ConcurrentHashMap<PublicKey, Connection>()
    protected val serverKeyPair = generateKeyPair()
    protected val logger = KotlinLogging.logger("RelayServer")
    protected val openMutex = Mutex(true)
    protected val random = SecureRandom()
    protected var state = Initialized
    protected var listenJob: Job? = null

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    suspend fun connectionStates(): List<Pair<String, Boolean>> {
        return connections.map { Pair(Id.ofPublicKey(it.key).toString(), it.value.isReady()) }
    }

    fun getUsage(): Sequence<Usage> {
        return (messageStore as? MemoryMessageStore)?.getUsage() ?: emptySequence()
    }

    protected abstract suspend fun authenticate(socketSession: SocketSession): PublicKey?

    // Decode the envelope into common server format
    protected abstract fun decodePayload(from: PublicKey, payload: ByteArray): Envelope

    // Turn the Envelope into a message that the client will understand
    protected abstract fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray

    protected fun isAuthorized(clientPublicKey: PublicKey): Boolean {
        // TODO: Support client lists
        logger.debug { "Authorizing client: ${Id.ofPublicKey(clientPublicKey)}" }
        return true
    }

    private val noPendingMessagesMessage = Message(
        serverKeyPair.public,
        ControlMessage(ControlMessageType.NO_PENDING_MESSAGES).encodeProto()
    )

    private fun getQueueEmptyEnvelope(to: PublicKey): Envelope {
        return Envelope.from(serverKeyPair.private, to, null, noPendingMessagesMessage)
    }

    private suspend fun sendStoredMessages(publicKey: PublicKey) {
        val connection = connections[publicKey] ?: return

        while (messageStore != null) {
            val storedMessage = messageStore.getMessages(publicKey).firstOrNull() ?: break
            connection.writeSizedByteArray(encodePayload(publicKey, storedMessage.envelope))
            messageStore.removeMessage(storedMessage)
        }

        if (messageStore != null) {
            logger.info { "Queue empty for: ${Id.ofPublicKey(publicKey)}" }
            connection.writeSizedByteArray(encodePayload(publicKey, getQueueEmptyEnvelope(publicKey)))
        }
    }

    suspend fun handleSession(socketSession: SocketSession) {
        authenticate(socketSession)?.let { publicKey ->
            val connection = Connection(socketSession, Id.ofPublicKey(publicKey).toString())
            logger.info { "Session authenticated for: ${connection.name}" }
            connections[publicKey] = connection

            try {
                sendStoredMessages(publicKey)

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

    private suspend fun deliverMessage(
        from: PublicKey,
        to: PublicKey,
        envelope: Envelope,
    ) {
        require(from != to) { "Attempt to deliver message to self" }
        val fromId = Id.ofPublicKey(from)
        val toId = Id.ofPublicKey(to)
        val prefix = "from=$fromId, to=$toId:"
        val messageBytes = encodePayload(to, envelope)
        var messageDelivered = false

        try {
            val connection = connections[to]

            if (connection == null) {
                logger.info { "$prefix no connection to receiver" }
            } else if (!connection.isReady()) {
                logger.info { "$prefix Removing closed connection for receiver" }
            } else {
                logger.info { "$prefix Delivering ${envelope.message.bytes.size} bytes" }
                connection.writeSizedByteArray(messageBytes)
                messageDelivered = true
            }
        } catch (e: Exception) {
            logger.error { "$prefix Error while handling message: $e" }
        } finally {
            try {
                if (!messageDelivered && envelope.messageStorageKey != null)
                    messageStore?.addMessage(from, to, envelope)
            } catch (e: Exception) {
                logger.error { "Error while storing message - from: $fromId to: $toId e: $e" }
            }
        }
    }

    private suspend fun handleMessage(from: PublicKey, payload: ByteArray) {
        val fromId = Id.ofPublicKey(from)
        try {
            decodePayload(from, payload).let { envelope ->
                envelope.recipients.forEach { recipient ->
                    try {
                        deliverMessage(from, recipient.publicKey, envelope)
                    } catch (e: Exception) {
                        logger.error { "Error while delivering message from: $fromId to: ${recipient.id()} - $e" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error { "Error while handling message from $fromId: $e" }
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