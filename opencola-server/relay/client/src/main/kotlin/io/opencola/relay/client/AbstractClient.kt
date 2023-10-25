package io.opencola.relay.client

import io.opencola.model.Id
import io.opencola.security.*
import io.opencola.relay.common.*
import io.opencola.relay.common.connection.Connection
import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.v2.ControlMessage
import io.opencola.relay.common.message.v2.ControlMessageType
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.MessageStorageKey
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.net.ConnectException
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.security.SecureRandom

abstract class AbstractClient(
    protected val uri: URI,
    protected val keyPair: KeyPair,
    protected val name: String? = null,
    protected val requestTimeoutMilliseconds: Long = 60000, // TODO: Make configurable
    private val retryPolicy: (Int) -> Long = retryExponentialBackoff(),
) : RelayClient {
    protected val logger = KotlinLogging.logger("RelayClient${if (name != null) " ($name)" else ""}")
    protected val hostname: String = uri.host
    protected val port = if (uri.port > 0) uri.port else defaultOCRPort
    protected var serverPublicKey: PublicKey? = null
    protected var eventHandler: EventHandler? = null
    protected val random = SecureRandom()
    protected val numChallengeBytes = 32

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

        this.eventHandler = { publicKey, event ->
            val id = Id.ofPublicKey(publicKey)
            logger.warn { "Unhandled event: $event for $id" }
        }
    }

    abstract suspend fun getSocketSession(): SocketSession

    // V1 Client can't support sending to multiple recipients in one payload, so multiple payload need to be supported - remove when V1 is deprecated
    protected abstract fun encodePayload(
        to: List<PublicKey>,
        messageStorageKey: MessageStorageKey,
        message: Message
    ): List<ByteArray>

    protected abstract fun decodePayload(payload: ByteArray): Envelope
    protected abstract suspend fun authenticate(socketSession: SocketSession)

    fun isAuthorized(serverPublicKey: PublicKey): Boolean {
        logger.info { "Authorizing server: ${Id.ofPublicKey(serverPublicKey)}" }
        return true
    }

    val publicKey: PublicKey
        get() = keyPair.public

    val state: State
        get() = _state

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    protected suspend fun getConnection(waitForOpen: Boolean = true): Connection {
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
                    _connection = Connection(keyPair.public, socketSession) {}
                    connectionFailures = 0
                    logger.info { "Connection created to $uri for ${_connection!!.id}" }
                } catch (e: Exception) {
                    // TODO: Max connectFailures?
                    connectionFailures++
                    throw e
                }
            }

            _connection!!
        }
    }

    override suspend fun setEventHandler(eventHandler: EventHandler) {
        this.eventHandler = eventHandler
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

    override suspend fun sendMessage(to: List<PublicKey>, key: MessageStorageKey, body: ByteArray) {
        try {
            val connection = getConnection()
            val message = Message(keyPair.public, body)

            // TODO: Should there be a limit on the size of messages?
            logger.info { "Sending message: $message" }

            // V1 does not support sending to multiple recipients in one payload, so multiple payload need to be supported - remove when V1 is deprecated
            encodePayload(to, key, message).forEach { payload ->
                withTimeout(requestTimeoutMilliseconds) {
                    connection.writeSizedByteArray(payload)
                }
            }
        } catch (e: ConnectException) {
            // Pass exception through so caller knows message wasn't sent
            throw e
        } catch (e: TimeoutCancellationException) {
            val toString = to.joinToString { Id.ofPublicKey(it).toString() }
            logger.warn { "Timeout sending message to: $toString" }
        } catch (e: CancellationException) {
            // Let exception flow through
        } catch (e: Exception) {
            logger.error { "Unexpected exception when sending message $e" }
        }
    }

    private fun isControlMessage(message: Message): Boolean {
        return message.from == serverPublicKey
    }

    private suspend fun handleControlMessage(message: Message) {
        val controlMessage = ControlMessage.decodeProto(message.body)

        when (controlMessage.type) {
            ControlMessageType.NONE -> {
                logger.error { "Received control message with type NONE" }
            }

            ControlMessageType.NO_PENDING_MESSAGES -> {
                eventHandler?.invoke(publicKey, RelayEvent.NO_PENDING_MESSAGES)
            }
        }
    }

    private suspend fun handleMessage(payload: ByteArray, messageHandler: MessageHandler) {
        try {
            val message = decodePayload(payload).decryptMessage(keyPair)
            logger.info { "Handling message: $message" }

            if (isControlMessage(message)) {
                handleControlMessage(message)
            } else {
                messageHandler(message.from, message.body)
            }
        } catch (e: Exception) {
            logger.error { "Exception in handleMessage: $e" }
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