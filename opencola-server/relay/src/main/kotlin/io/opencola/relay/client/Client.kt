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

private fun PublicKey.asId() : Id {
    return Id.ofPublicKey(this)
}

// TODO: Add name and connection timeout and retry policy parameters
class Client(
    private val hostname: String,
    private val port: Int,
    private val keyPair: KeyPair,
    private val requestTimeoutMilliseconds: Long = 5000, // TODO: What's the right value here?
) : Closeable {
    private val logger = KotlinLogging.logger("Client")

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null
    private val connectionMutex = Mutex()
    private val openMutex = Mutex(true)
    private val sessions = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray?>>()
    private var closed = false
    private var connectionFailures = 0

    suspend fun waitUntilOpen() {
        openMutex.withLock {  }
    }

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

    private suspend fun getConnection(waitForOpen: Boolean = true) : Connection {
        if(closed)
            throw IllegalStateException("Can't get connection on a Client that has been closed")

        if(waitForOpen) {
            // TODO: Add connection timeout
            openMutex.withLock { }
        }

        return connectionMutex.withLock {
            if (_connection == null || !_connection!!.isReady()) {
                // TODO: Move to open
                if(connectionFailures > 0) {
                    // TODO: Factor out retry policy
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

    private suspend fun handleMessage(payload: ByteArray, handler: suspend (ByteArray) -> ByteArray) {
        try {
            val message = Message.decode(decrypt(keyPair.private, payload))
            val sessionResult = sessions[message.header.sessionId]

            if(sessionResult != null) {
                sessions.remove(message.header.sessionId)
                sessionResult.complete(message.body)
            } else {
                respondToMessage(message.header, handler(message.body))
            }
        } catch(e: Exception){
            logger.error { "Exception in handleMessage: $e" }
        }
    }

    suspend fun open(messageHandler: suspend (ByteArray) -> ByteArray) {
        // TODO: This doesn't look right. openMutex could be locked briefly get getConnection
        if (!openMutex.isLocked) {
            throw IllegalStateException("Client is already opened")
        }

        while (!closed) {
            try {
                if (!openMutex.isLocked)
                    openMutex.lock()
                getConnection(false).also {
                    openMutex.unlock()
                    it.listen { payload -> handleMessage(payload, messageHandler) }
                }
            } catch (e: CancellationException) {
                break
            } catch (e: Exception) {
                logger.error { "Exception during listen: $e" }
            }
        }
    }

    override fun close() {
        closed = true
        _connection?.close()
        _connection = null
    }

    suspend fun sendMessage(to: PublicKey, body: ByteArray ): ByteArray? {
        val message = Message(Header(keyPair.public, UUID.randomUUID()), body)
        val envelope = MessageEnvelope(to, message)
        val deferredResult = CompletableDeferred<ByteArray?>()

        return try {
            sessions[message.header.sessionId] = deferredResult
            getConnection().writeSizedByteArray(MessageEnvelope.encode(envelope))

            // TODO: Configure timeout
            withTimeout(requestTimeoutMilliseconds) {
                deferredResult.await()
            }
        } catch(e: ConnectException) {
            null
        }
        catch (e: TimeoutCancellationException) {
            null
        }
    }

    suspend fun respondToMessage(messageHeader: Header, body: ByteArray) {
        val responseMessage = Message(Header(keyPair.public, messageHeader.sessionId), body)
        val envelope = MessageEnvelope(messageHeader.from, responseMessage)

        // TODO: This can fail, if server goes down. Should failure propagate?
        withTimeout(requestTimeoutMilliseconds) {
            getConnection().writeSizedByteArray(MessageEnvelope.encode(envelope))
        }
    }

    companion object {
        init{
            initProvider()
        }

        private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    }
}