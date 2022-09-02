package io.opencola.relay.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.security.initProvider
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import opencola.core.extensions.toByteArray
import io.opencola.relay.common.Connection
import io.opencola.relay.common.MessageEnvelope
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RelayServer(port: Int) {
    private val logger = KotlinLogging.logger("RelayServer")
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
    private val connectionHandlers = ConcurrentHashMap<PublicKey, Connection>()
    private var started = false

    fun isStarted() : Boolean {
        return started
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

    private val handleMessage: suspend (ByteArray) -> Unit = { message ->
        // TODO: Disallow sending messages to self
        val envelope = MessageEnvelope.decode(message)
        val connection = connectionHandlers[envelope.to]

        if(connection == null) {
            logger.info { "Received message for peer that is not connected" }
            // TODO: Signal back to client
        } else {
            connection.writeSizedByteArray(envelope.message)
        }
    }

    suspend fun run() = coroutineScope() {
        selectorManager.use {
            serverSocket.use {
                logger.info("Relay Server listening at ${serverSocket.localAddress}")
                started = true
                while (isActive) {
                    val socket = serverSocket.accept()
                    val connection = Connection(socket)
                    val publicKey = authenticate(connection)

                    if (publicKey != null) {
                        logger.info { "Connection Authenticated for: ${Id.ofPublicKey(publicKey)}" }
                        connectionHandlers[publicKey] = connection
                        launch { connection.use { it.listen(handleMessage) } }
                    }
                }
            }
        }
    }

    companion object {
        init {
            initProvider()
        }
    }
}