package io.opencola.relay.server

import io.opencola.core.model.Id
import io.opencola.core.security.initProvider
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.relay.common.Connection
import io.opencola.relay.common.MessageEnvelope
import io.opencola.relay.common.SocketSession
import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractRelayServer(
    private val numChallengeBytes: Int = 32
) {
    protected val logger = KotlinLogging.logger("RelayServer")
    private val connections = ConcurrentHashMap<PublicKey, Connection>()
    protected val openMutex = Mutex(true)
    private val random = SecureRandom()
    protected var state = Initialized
    protected var listenJob: Job? = null

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    suspend fun connectionStates() : List<Pair<String, Boolean>> {
        return connections.map { Pair(Id.ofPublicKey(it.key).toString(), it.value.isReady()) }
    }

    private suspend fun authenticate(socketSession: SocketSession): PublicKey? {
        try {
            val encodedPublicKey = socketSession.readSizedByteArray()
            val publicKey = publicKeyFromBytes(encodedPublicKey)

            // Send challenge
            val challenge = ByteArray(numChallengeBytes).also { random.nextBytes(it) }
            socketSession.writeSizedByteArray(challenge)

            // Read signed challenge
            val challengeSignature = socketSession.readSizedByteArray()

            val status = if (isValidSignature(publicKey, challenge, challengeSignature)) 0 else -1
            socketSession.writeSizedByteArray(IntByteArrayCodec.encode(status))
            if (status != 0)
                throw RuntimeException("Challenge signature is not valid")

            return publicKey
        } catch (e: CancellationException) {
            // Let job cancellation fall through
        } catch (e: Exception) {
            logger.warn { "Client failed to authenticate: $e" }
            socketSession.close()
        }

        return null
    }

    private suspend fun handleMessage(from: PublicKey, payload: ByteArray) {
        val fromId = Id.ofPublicKey(from)

        try {
            val envelope = MessageEnvelope.decode(payload)
            val toId = Id.ofPublicKey(envelope.to)
            val prefix = "from=$fromId, to=$toId:"

            if (from != envelope.to) {
                val connection = connections[envelope.to]

                if (connection == null) {
                    logger.info { "$prefix no connection to receiver" }
                    return
                } else if (!connection.isReady()) {
                    logger.info { "$prefix Removing closed connection for receiver" }
                    connections.remove(envelope.to)
                } else {
                    logger.info { "$prefix Delivering ${envelope.message.size} bytes" }
                    connection.writeSizedByteArray(envelope.message)
                }
            }
        } catch (e: Exception) {
            logger.error { "Error while handling message from $fromId: $e" }
        }
    }

    suspend fun handleSession(socketSession: SocketSession) {
        authenticate(socketSession)?.let { publicKey ->
            val connection = Connection(socketSession, Id.ofPublicKey(publicKey).toString())
            logger.info { "Session authenticated for: ${connection.name}" }
            connections[publicKey] = connection

            try {
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