package io.opencola.relay.client

import io.opencola.model.Id
import io.opencola.security.*
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

abstract class AbstractClient(
    protected val uri: URI,
    protected val keyPair: KeyPair,
    protected val name: String? = null,
    private val requestTimeoutMilliseconds: Long = 60000, // TODO: Make configurable
    private val retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : RelayClient {
    protected val logger = KotlinLogging.logger("RelayClient${if (name != null) " ($name)" else ""}")
    protected val hostname: String = uri.host
    protected val port = if (uri.port > 0) uri.port else defaultOCRPort

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null

    private var _state = Initialized
    private val connectionMutex = Mutex()
    private val openMutex = Mutex(true)
    private var connectionFailures = 0
    private var listenJob: Job? = null

    init {
        if (uri.scheme != "ocr")
            throw IllegalArgumentException("Scheme must be 'ocr' for relay URI: $uri")
    }

    abstract suspend fun getSocketSession(): SocketSession
    protected abstract suspend fun authenticate(socketSession: SocketSession)
    // TODO: This looks wrong. Maybe envelope should be encoded in connection?
    //  then client only needs to worry about encoding the message.
    protected abstract fun encodeEnvelope(envelope: Envelope): ByteArray
    protected abstract fun decodeMessage(bytes: ByteArray): Message


    val publicKey: PublicKey
        get() = keyPair.public

    val state: State
        get() = _state

    override suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    private suspend fun getConnection(waitForOpen: Boolean = true): Connection {
        if (_state == Closed)
            throw IllegalStateException("Can't get connection on a Client that has been closed")

        if (waitForOpen) {
            // Block other callers while connection is being opened so that retries are properly spaced.
            // open() manages this lock
            openMutex.withLock { }
        }

        return connectionMutex.withLock {
            if (_state != Closed && _connection == null || !_connection!!.isReady()) {

                if (connectionFailures > 0) {
                    val delayInMilliseconds = retryPolicy(connectionFailures)
                    logger.warn { "Connection failure: Waiting $delayInMilliseconds ms to connect" }
                    delay(delayInMilliseconds)
                }

                try {
                    val socketSession = getSocketSession()
                    authenticate(socketSession)
                    _connection = Connection(socketSession, name)
                    connectionFailures = 0
                    logger.info { "Connection created to $uri for ${Id.ofPublicKey(keyPair.public)}" }
                } catch (e: Exception) {
                    // TODO: Max connectFailures?
                    connectionFailures++
                    throw e
                }
            }

            _connection!!
        }
    }

    override suspend fun open(messageHandler: MessageHandler) = coroutineScope {
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
                        if (_state == Opening) _state = Open
                        openMutex.unlock()
                        it.listen { payload -> handleMessage(payload, messageHandler) }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: InterruptedException) {
                    break
                } catch (e: ConnectException) {
                    // This can happen when partitioned from the server
                    continue
                } catch (e: Exception) {
                    logger.error { "Exception during listen: $e" }
                }
            }
        }
    }

    private suspend fun handleMessage(payload: ByteArray, messageHandler: MessageHandler) {
        try {
            val message = decodeMessage(decrypt(keyPair.private, payload)).validate()
            logger.info { "Handling message: ${message.header}" }
            messageHandler(message.header.from, message.body)
        } catch (e: Exception) {
            logger.error { "Exception in handleMessage: $e" }
        }
    }

    override suspend fun sendMessage(to: PublicKey, body: ByteArray) {
        val message = Message(keyPair, body)
        val envelope = Envelope(to, null, message)

        try {
            // TODO: Should there be a limit on the size of messages?
            logger.info { "Sending message: ${message.header}" }
            withTimeout(requestTimeoutMilliseconds) {
                getConnection().writeSizedByteArray(encodeEnvelope(envelope))
            }
        } catch (e: ConnectException) {
            // Pass exception through so caller knows message wasn't sent
            throw e
        } catch (e: TimeoutCancellationException) {
            logger.warn { "Timeout sending message to: ${Id.ofPublicKey(to)}" }
        } catch (e: CancellationException) {
            // Let exception flow through
        } catch (e: Exception) {
            logger.error { "Unexpected exception when sending message $e" }
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

    companion object {
        init {
            initProvider()
        }
    }
}