package io.opencola.relay.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.network.NetworkNode
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mu.KotlinLogging
import opencola.core.extensions.toByteArray
import io.opencola.relay.common.Connection
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class RelayServer(private val port: Int) {
    private val logger = KotlinLogging.logger("RelayServer")
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
    private val connectionHandlers = ConcurrentHashMap<PublicKey, ConnectionHandler>()
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

    suspend fun run() = coroutineScope() {
        try {
            logger.info("Relay Server listening at ${serverSocket.localAddress}")
            started = true
            while (isActive) {
                logger.info { "Waiting for connections" }
                val socket = serverSocket.accept()
                logger.info("Accepted ${socket.remoteAddress}")
                val connection = Connection(socket)
                val publicKey = authenticate(connection)

                if(publicKey != null) {
                    val connectionHandler = ConnectionHandler(connection)
                    connectionHandlers[publicKey] = connectionHandler
                    launch { connectionHandler.use { it.start() } }
                }
            }
        } finally {
            serverSocket.close()
            selectorManager.close()
        }
    }

}