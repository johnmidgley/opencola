package io.opencola.relay.client

import io.opencola.core.model.Id
import io.opencola.core.security.*
import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.relay.common.*
import io.opencola.relay.common.Connection
import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.net.ConnectException
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap

const val defaultOCRPort = 2652

abstract class AbstractClient(
    protected val uri: URI,
    private val keyPair: KeyPair,
    final override val name: String? = null,
    private val requestTimeoutMilliseconds: Long = 20000,
    private val retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : RelayClient {
    protected val logger = KotlinLogging.logger("Client${if(name != null) " ($name)" else ""}")
    protected val hostname: String = uri.host
    protected val port = if(uri.port > 0) uri.port else defaultOCRPort

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null

    private var _state = Initialized
    private val connectionMutex = Mutex()
    private val openMutex = Mutex(true)
    private val sessions = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray?>>()
    private var connectionFailures = 0
    private var listenJob: Job? = null

    init {
        if(uri.scheme != "ocr")
            throw IllegalArgumentException("Scheme must be 'ocr' for relay URI: $uri")
    }

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
            if (_state != Closed && _connection == null || !_connection!!.isReady()) {

                if(connectionFailures > 0) {
                    val delayInMilliseconds = retryPolicy(connectionFailures)
                    logger.warn{ "Waiting $delayInMilliseconds ms to connect" }
                    delay(delayInMilliseconds)
                }

                try {
                    val socketSession = getSocketSession()
                    authenticate(socketSession)
                    _connection = Connection(socketSession, name)
                    connectionFailures = 0
                    logger.info { "Connection created for ${Id.ofPublicKey(keyPair.public)}" }
                } catch (e: Exception) {
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
            logger.info { "Handling message" }
            val message = Message.decode(decrypt(keyPair.private, payload)).validate()
            val sessionResult = sessions[message.header.sessionId]

            logger.info { "SessionId: ${message.header.sessionId}" }

            if(sessionResult != null) {
                sessions.remove(message.header.sessionId)
                // TODO: Handle late arriving responses
                sessionResult.complete(message.body)
            } else {
                // respondToMMessage will apply request timeout
                // TODO: It is a bad assumption that unrecognized session ids indicate a request that needs a response.
                //  This can happen in a partitioning situation, where a response with an unknown session id is received.
                //  FIX THIS in next version of relay protocol. For now, it will result in an error that won't be
                //  responded to
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
                        if(_state == Opening) _state = Open
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
        logger.info { "Closed - $name" }
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
            logger.error { "Failed connect when sending message" }
            null
        } catch (e: TimeoutCancellationException) {
            logger.warn { "Timeout sending message to: ${Id.ofPublicKey(to)}" }
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
            logger.error { "Failed connect when sending response" }
        } catch (e: TimeoutCancellationException) {
            logger.error { "Timeout sending response" }
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