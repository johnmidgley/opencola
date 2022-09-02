package io.opencola.relay.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.security.decrypt
import io.opencola.core.security.initProvider
import io.opencola.core.security.sign
import io.opencola.relay.common.Connection
import io.opencola.relay.common.Header
import io.opencola.relay.common.Message
import io.opencola.relay.common.MessageEnvelope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.io.Closeable
import java.lang.Long.min
import java.net.ConnectException
import java.security.KeyPair
import java.security.PublicKey
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class Client(private val hostname: String, private val port: Int, private val keyPair: KeyPair) : Closeable {
    private val logger = KotlinLogging.logger("Client")

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null
    private val connectionMutex = Mutex() // TODO: Not needed anymore, is it?
    private val sessions = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray?>>()
    private var open = true
    private var connectionFailures = 0

    // Should only be called once, right after connection to server
    private suspend fun authenticate(connection: Connection) {
        // Send public key
        connection.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        val challengeBytes = connection.readSizedByteArray()

        // Sign challenge and send back
        connection.writeSizedByteArray(sign(keyPair.private, challengeBytes))

        val authenticationResponse = connection.readInt()
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }
    }

    private suspend fun getConnection() : Connection {
        if(!open)
            throw IllegalStateException("Can't get connection on a Client that has been closed")

        return connectionMutex.withLock {
            if (_connection == null || !_connection!!.isReady()) {
                if(connectionFailures > 0) {
                    val delayInSeconds = min(1L.shl(connectionFailures), Duration.ofHours(1).seconds)
                    logger.warn{ "Waiting $delayInSeconds seconds to connect" }

                    delay(delayInSeconds * 1000)
                }

                logger.info { "Creating Connection for: ${Id.ofPublicKey(keyPair.public)}" }

                try {
                    _connection = Connection(aSocket(selectorManager).tcp().connect(hostname, port = port)).also {
                        authenticate(it)
                    }
                    connectionFailures = 0
                } catch (e: ConnectException) {
                    connectionFailures++
                    throw e
                }
            }

            _connection!!
        }
    }

    // This needs to be called before messages can be received from peers. Requests automatically open connections too.
    suspend fun connect() {
        getConnection()
    }

    private suspend fun respondToMessage(messageHeader: Header, body: ByteArray) {
        val responseMessage = Message(Header(keyPair.public, messageHeader.sessionId), body)
        val envelope = MessageEnvelope(messageHeader.from, responseMessage)
        getConnection().writeSizedByteArray(MessageEnvelope.encode(envelope))
    }

    suspend fun sendMessage(to: PublicKey, body: ByteArray ): ByteArray? {
        val message = Message(Header(keyPair.public, UUID.randomUUID()), body)
        val envelope = MessageEnvelope(to, message)
        val deferredResult = CompletableDeferred<ByteArray?>()

        sessions[message.header.sessionId] = deferredResult
        getConnection().writeSizedByteArray(MessageEnvelope.encode(envelope))

        return try {
            // TODO: Configure timeout
            withTimeout(5000) {
                deferredResult.await()
            }
        } catch (e: TimeoutCancellationException) {
            null
        }
    }

    private val handleMessage: suspend (ByteArray) -> Unit = { payload ->
        try {
            val message = Message.decode(decrypt(keyPair.private, payload))
            val sessionResult = sessions[message.header.sessionId]

            if(sessionResult != null) {
                sessions.remove(message.header.sessionId)
                sessionResult.complete(message.body)
            } else {
                // TODO: Use handler passed into client for this
                respondToMessage(message.header, message.body)
            }
        } catch(e: Exception){
            logger.error { "Exception in handleMessage: $e" }
        }
    }

    // NOTE: This is the only place reads should occur, outside authentication
    suspend fun listen() = coroutineScope {
        while(isActive && open){
            try {
                getConnection().listen(handleMessage)
            } catch (e: CancellationException) {
                return@coroutineScope
            } catch (e: Exception) {
                logger.error { "Exception during listen: $e" }
            }
        }
    }

    override fun close() {
        open = false
        _connection?.close()
        _connection = null
    }

    companion object {
        init{
            initProvider()
        }

        private val selectorManager = ActorSelectorManager(Dispatchers.IO)
        private val emptyByteArray = ByteArray(0)
    }
}