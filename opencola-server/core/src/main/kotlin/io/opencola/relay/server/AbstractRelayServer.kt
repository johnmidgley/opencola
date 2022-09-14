package io.opencola.relay.server

import io.opencola.core.model.Id
import io.opencola.core.security.initProvider
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.core.serialization.IntByteArrayCodec
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
        try {
            val envelope = MessageEnvelope.decode(payload)

            if (from != envelope.to) {
                val connection = connections[envelope.to]

                if (connection == null)
                    return
                else if (!connection.isReady()) {
                    logger.debug { "Removing closed connection for: ${Id.ofPublicKey(envelope.to)}" }
                    connections.remove(envelope.to)
                } else
                    connection.writeSizedByteArray(envelope.message)
            }
        } catch (e: Exception) {
            logger.error { "Error while handling message: $e" }
        }
    }

    suspend fun handleSession(socketSession: SocketSession) {
        authenticate(socketSession)?.let { publicKey ->
            val connection = Connection(socketSession, Id.ofPublicKey(publicKey).toString())
            logger.info { "Connection Authenticated for: ${connection.name}" }
            connections[publicKey] = connection
            try {
                connection.listen { payload -> handleMessage(publicKey, payload) }
            } finally {
                connection.close()
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