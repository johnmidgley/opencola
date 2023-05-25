package io.opencola.relay.server

import io.opencola.model.Id
import io.opencola.security.initProvider
import io.opencola.relay.common.Connection
import io.opencola.relay.common.Envelope
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
    protected val numChallengeBytes: Int = 32
) {
    protected val logger = KotlinLogging.logger("RelayServer")
    protected val connections = ConcurrentHashMap<PublicKey, Connection>()
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

    protected abstract suspend fun authenticate(socketSession: SocketSession): PublicKey?
    protected abstract fun decodePayload(payload: ByteArray): Envelope

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

    private suspend fun handleMessage(from: PublicKey, payload: ByteArray) {
        val fromId = Id.ofPublicKey(from)

        try {
            val envelope = decodePayload(payload)
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