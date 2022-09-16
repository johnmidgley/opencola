package io.opencola.relay.client

import io.opencola.core.model.Id
import io.opencola.core.security.*
import io.opencola.core.serialization.IntByteArrayCodec
import io.opencola.relay.common.*
import io.opencola.relay.common.Connection
import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.net.ConnectException
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractClient(
    protected val hostname: String,
    protected val port: Int,
    private val keyPair: KeyPair,
    final override val name: String? = null,
    private val requestTimeoutMilliseconds: Long = 10000,
    private val retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : Client {
    protected val logger = KotlinLogging.logger("Client${if(name != null) " ($name)" else ""}")

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null
    private var _state = Initialized
    private val connectionMutex = Mutex()
    private val openMutex = Mutex(true)
    private val sessions = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray?>>()
    private var connectionFailures = 0
    private var listenJob: Job? = null

    override val publicKey : PublicKey
        get() = keyPair.public

    override val state : State
        get() = _state

    override suspend fun waitUntilOpen() {
        openMutex.withLock {  }
    }

    // Should only be called once, right after connection to server
    private suspend fun authenticate(socketSession: SocketSession) {
        // Send public key
        socketSession.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        val challengeBytes = socketSession.readSizedByteArray()

        // Sign challenge and send back
        socketSession.writeSizedByteArray(sign(keyPair.private, challengeBytes))

        val authenticationResponse = IntByteArrayCodec.decode(socketSession.readSizedByteArray())
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }
    }

    abstract suspend fun getSocketSession(): SocketSession

    private suspend fun getConnection(waitForOpen: Boolean = true) : Connection {
        if(_state == Closed)
            throw IllegalStateException("Can't get connection on a Client that has been closed")

        if(waitForOpen) {
            // Block other callers while connection is being opened so that retries are properly spaced.
            // open() manages this lock
            openMutex.withLock { }
        }

        return connectionMutex.withLock {
            if (_connection == null || !_connection!!.isReady()) {

                if(connectionFailures > 0) {
                    val delayInMilliseconds = retryPolicy(connectionFailures)
                    logger.warn{ "Waiting $delayInMilliseconds ms to connect" }
                    delay(delayInMilliseconds)
                }

                logger.info { "Creating Connection for: ${Id.ofPublicKey(keyPair.public)} at $hostname:$port" }

                try {
                    val socketSession = getSocketSession()
                    authenticate(socketSession)
                    _connection = Connection(socketSession, name)
                    connectionFailures = 0
                } catch (e: ConnectException) {
                    // TODO: Max connectFailures?
                    connectionFailures++
                    throw e
                }
            }

            _connection!!
        }
    }

    private suspend fun handleMessage(payload: ByteArray, handler: suspend (PublicKey, ByteArray) -> ByteArray) {
        try {
            val message = Message.decode(decrypt(keyPair.private, payload)).validate()
            val sessionResult = sessions[message.header.sessionId]

            if(sessionResult != null) {
                sessions.remove(message.header.sessionId)
                sessionResult.complete(message.body)
            } else {
                // respondToMMessage will apply request timeout
                respondToMessage(message.header, handler(message.header.from, message.body))
            }
        } catch(e: Exception){
            logger.error { "Exception in handleMessage: $e" }
        }
    }

    override suspend fun open(messageHandler: suspend (PublicKey, ByteArray) -> ByteArray) = coroutineScope {
        if (_state != Initialized) {
            throw IllegalStateException("Client has already been opened")
        }
        _state = Opening

        listenJob = launch {
            while (_state != Closed) {
                try {
                    if (!openMutex.isLocked)
                        openMutex.lock()

                    getConnection(false).also {
                        _state = Open
                        openMutex.unlock()
                        it.listen { payload -> handleMessage(payload, messageHandler) }
                    }
                } catch (e: CancellationException) {
                    break
                } catch(e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    logger.error { "Exception during listen: $e" }
                }
            }
        }
    }

    override suspend fun close() {
        _state = Closed
        listenJob?.cancel()
        listenJob = null
        _connection?.close()
        _connection = null
    }

    override suspend fun sendMessage(to: PublicKey, body: ByteArray): ByteArray? {
        val message = Message(keyPair, UUID.randomUUID(), body)
        val envelope = MessageEnvelope(to, message)
        val deferredResult = CompletableDeferred<ByteArray?>()

        return try {
            sessions[message.header.sessionId] = deferredResult

            withTimeout(requestTimeoutMilliseconds) {
                getConnection().writeSizedByteArray(MessageEnvelope.encode(envelope))
                deferredResult.await()
            }
        } catch (e: ConnectException) {
            logger.debug { "Failed connect when sending message" }
            null
        } catch (e: TimeoutCancellationException) {
            logger.debug { "Timeout sending message" }
            null
        } catch (e: CancellationException) {
            // Let exception flow through
            null
        } catch (e: Exception) {
            logger.error { "Unexpected exception when sending message $e" }
            null
        }
    }

    override suspend fun respondToMessage(messageHeader: Header, body: ByteArray) {
        val responseMessage = Message(keyPair, messageHeader.sessionId, body)
        val envelope = MessageEnvelope(messageHeader.from, responseMessage)

        try {
            withTimeout(requestTimeoutMilliseconds) {
                getConnection().writeSizedByteArray(MessageEnvelope.encode(envelope))
            }
        } catch (e: ConnectException) {
            logger.debug { "Failed connect when sending response" }
        } catch (e: TimeoutCancellationException) {
            logger.debug { "Timeout sending response" }
        } catch (e: CancellationException) {
            // Let exception flow through
        } catch (e: Exception) {
            logger.error { "Unexpected exception when sending response $e" }
        }
    }

    companion object {
        init{
            initProvider()
        }
    }
}