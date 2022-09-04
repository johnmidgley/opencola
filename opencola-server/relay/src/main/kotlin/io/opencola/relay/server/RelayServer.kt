package io.opencola.relay.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.security.initProvider
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.relay.common.Connection
import io.opencola.relay.common.MessageEnvelope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import opencola.core.extensions.toByteArray
import java.io.Closeable
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RelayServer(port: Int): Closeable {
    private val logger = KotlinLogging.logger("RelayServer")
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
    private val connections = ConcurrentHashMap<PublicKey, Connection>()
    private val openMutex = Mutex(true)
    private var closed = false

    suspend fun waitUntilOpen() {
        openMutex.withLock {  }
    }

    private suspend fun authenticate(connection: Connection): PublicKey? {
        try {
            val encodedPublicKey = connection.readSizedByteArray()
            val publicKey = publicKeyFromBytes(encodedPublicKey)

            // Send challenge
            // TODO: Use secure random bit generation
            val challenge = UUID.randomUUID().toByteArray()
            connection.writeSizedByteArray(challenge)

            // Read signed challenge
            val challengeSignature = connection.readSizedByteArray()

            val status = if (isValidSignature(publicKey, challenge, challengeSignature)) 0 else -1
            connection.writeInt(status)
            if (status != 0)
                throw RuntimeException("Challenge signature is not valid")

            return publicKey
        } catch (e: Exception) {
            // TODO: Fix anti-pattern of catch all. Coroutines can be canceled, and this stops propagation.
            logger.warn { "Client failed to authenticate: $e" }
            connection.close()
        }

        return null
    }

    private suspend fun handleMessage(from: PublicKey, payload: ByteArray) {
        val envelope = MessageEnvelope.decode(payload)

        if (from != envelope.to) {
            // TODO: Check if connection open and or catch exception and remove bad connection
            connections[envelope.to]?.writeSizedByteArray(envelope.message)
        }
    }

    suspend fun open() = coroutineScope() {
        if (!openMutex.isLocked) {
            throw IllegalStateException("Server is already opened")
        }

        logger.info("Relay Server listening at ${serverSocket.localAddress}")
        openMutex.unlock()

        while (isActive && !closed) {
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
                if (closed || e is CancellationException) {
                    close()
                    break
                }
                else {
                    logger.error { "Error accepting connection: $e" }
                    throw e
                }
            }
        }
    }

    override fun close() {
        closed = true
        connections.values.forEach{ it.close() }
        connections.clear()
        serverSocket.close()
        selectorManager.close()
    }

    companion object {
        init {
            initProvider()
        }
    }
}