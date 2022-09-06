package io.opencola.relay.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.security.initProvider
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.relay.common.Connection
import io.opencola.relay.common.MessageEnvelope
import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.io.Closeable
import java.security.PublicKey
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class RelayServer(
    port: Int,
    private val numChallengeBytes: Int = 32
) : Closeable {
    private val logger = KotlinLogging.logger("RelayServer")
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
    private val connections = ConcurrentHashMap<PublicKey, Connection>()
    private val openMutex = Mutex(true)
    private val random = SecureRandom()
    private var state = Initialized
    private var listenJob: Job? = null

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    private suspend fun authenticate(connection: Connection): PublicKey? {
        try {
            val encodedPublicKey = connection.readSizedByteArray()
            val publicKey = publicKeyFromBytes(encodedPublicKey)

            // Send challenge
            val challenge = ByteArray(numChallengeBytes).also { random.nextBytes(it) }
            connection.writeSizedByteArray(challenge)

            // Read signed challenge
            val challengeSignature = connection.readSizedByteArray()

            val status = if (isValidSignature(publicKey, challenge, challengeSignature)) 0 else -1
            connection.writeInt(status)
            if (status != 0)
                throw RuntimeException("Challenge signature is not valid")

            return publicKey
        } catch (e: CancellationException) {
            // Let job cancellation fall through
        } catch (e: Exception) {
            logger.warn { "Client failed to authenticate: $e" }
            connection.close()
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

    suspend fun open() = coroutineScope() {
        if (state != Initialized) {
            throw IllegalStateException("Server has already been opened")
        }

        state = Opening

        listenJob = launch {
            logger.info("Relay Server listening at ${serverSocket.localAddress}")
            state = Open
            openMutex.unlock()

            while (state != Closed) {
                try {
                    val socket = serverSocket.accept()
                    val connection = Connection(socket)
                    val publicKey = authenticate(connection)

                    if (publicKey != null) {
                        logger.info { "Connection Authenticated for: ${Id.ofPublicKey(publicKey)}" }
                        connections[publicKey] = connection
                        launch { connection.use { it.listen { payload -> handleMessage(publicKey, payload) } } }
                    }
                } catch (e: Exception) {
                    if (state == Closed || e is CancellationException) {
                        close()
                        break
                    } else {
                        logger.error { "Error accepting connection: $e" }
                        throw e
                    }
                }
            }
        }
    }

    override fun close() {
        state = Closed
        connections.values.forEach { it.close() }
        connections.clear()
        listenJob?.cancel()
        listenJob = null
        serverSocket.close()
        selectorManager.close()
    }

    companion object {
        init {
            initProvider()
        }
    }
}