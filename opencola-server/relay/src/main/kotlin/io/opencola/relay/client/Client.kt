package io.opencola.relay.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.security.decrypt
import io.opencola.core.security.initProvider
import io.opencola.core.security.sign
import io.opencola.relay.common.*
import io.opencola.relay.common.Connection
import io.opencola.relay.common.State.*
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

// TODO: Retry policy parameters
class Client(
    private val hostname: String,
    private val port: Int,
    private val keyPair: KeyPair,
    private val requestTimeoutMilliseconds: Long = 10000,
    val name: String? = null
) : Closeable {
    private val logger = KotlinLogging.logger("Client${if(name != null) " ($name)" else ""}")

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null
    private val connectionMutex = Mutex()
    private val openMutex = Mutex(true)
    private val sessions = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray?>>()
    private var state = Initialized
    private var connectionFailures = 0
    private var listenJob: Job? = null

    val publicKey : PublicKey
        get() = keyPair.public

    suspend fun waitUntilOpen() {
        openMutex.withLock {  }
    }

    // Should only be called once, right after connection to server
    private suspend fun authenticate(connectedSocket: ConnectedSocket) {
        // Send public key
        connectedSocket.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        val challengeBytes = connectedSocket.readSizedByteArray()

        // Sign challenge and send back
        connectedSocket.writeSizedByteArray(sign(keyPair.private, challengeBytes))

        val authenticationResponse = connectedSocket.readChannel.readInt()
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }
    }

    private suspend fun getConnection(waitForOpen: Boolean = true) : Connection {
        if(state == Closed)
            throw IllegalStateException("Can't get connection on a Client that has been closed")

        if(waitForOpen) {
            // Block other callers while connection is being opened so that retries are properly spaced.
            // open() manages this lock
            openMutex.withLock { }
        }

        return connectionMutex.withLock {
            if (_connection == null || !_connection!!.isReady()) {
                if(connectionFailures > 0) {
                    // TODO: Factor out retry policy
                    val delayInSeconds = min(1L.shl(connectionFailures), Duration.ofHours(1).seconds)
                    logger.warn{ "Waiting $delayInSeconds seconds to connect" }

                    delay(delayInSeconds * 1000)
                }

                logger.info { "Creating Connection for: ${Id.ofPublicKey(keyPair.public)}" }

                try {
                    val connectedSocket = ConnectedSocket(aSocket(selectorManager).tcp().connect(hostname, port = port))
                    authenticate(connectedSocket)
                    _connection = Connection(connectedSocket, name)
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
                // respondToMMessage will apply request timeout
                respondToMessage(message.header, handler(message.body))
            }
        } catch(e: Exception){
            logger.error { "Exception in handleMessage: $e" }
        }
    }

    suspend fun open(messageHandler: suspend (ByteArray) -> ByteArray) = coroutineScope {
        if (state != Initialized) {
            throw IllegalStateException("Client has already been opened")
        }

        state = Opening

        listenJob = launch {
            while (state != Closed) {
                try {
                    if (!openMutex.isLocked)
                        openMutex.lock()

                    getConnection(false).also {
                        state = Open
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
    }

    override fun close() {
        state = Closed
        listenJob?.cancel()
        listenJob = null
        _connection?.close()
        _connection = null
    }

    suspend fun sendMessage(to: PublicKey, body: ByteArray): ByteArray? {
        val message = Message(Header(keyPair.public, UUID.randomUUID()), body)
        val envelope = MessageEnvelope(to, message)
        val deferredResult = CompletableDeferred<ByteArray?>()

        return try {
            sessions[message.header.sessionId] = deferredResult
            val connection = getConnection()

            withTimeout(requestTimeoutMilliseconds) {
                connection.writeSizedByteArray(MessageEnvelope.encode(envelope))
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
        val connection = getConnection()

        // TODO: This can fail, if server goes down. Should failure propagate?
        withTimeout(requestTimeoutMilliseconds) {
            connection.writeSizedByteArray(MessageEnvelope.encode(envelope))
        }
    }

    companion object {
        init{
            initProvider()
        }

        private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    }
}