package io.opencola.relay.client

import io.opencola.model.Id
import io.opencola.security.*
import io.opencola.relay.common.*
import io.opencola.relay.common.State
import io.opencola.relay.common.connection.Connection
import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.SocketSession
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.*
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
    protected val connectTimeoutMilliseconds: Long = 3000, // TODO: Make configurable
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
    protected abstract suspend fun authenticate(socketSession: SocketSession): AuthenticationStatus

    fun isAuthorized(serverPublicKey: PublicKey): Boolean {
        logger.info { "Authorizing server: ${Id.ofPublicKey(serverPublicKey)}" }
        return true
    }

    val publicKey: PublicKey
        get() = keyPair.public

    val id: Id by lazy { Id.ofPublicKey(keyPair.public) }

    val state: State
        get() = _state

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    protected suspend fun connect(waitForOpen: Boolean = true): Connection? {
        if (_state == Closed)
            throw IllegalStateException("Can't get connection on a Client that has been closed")

        if (waitForOpen) {
            // Block other callers while connection is being opened so that retries are properly spaced.
            // open() manages this lock
            openMutex.withLock { }
        }

        connectionMutex.withLock {
            if (_state != Closed && _connection == null || !_connection!!.isReady()) {
                if (connectionFailures > 0) {
                    val delayInMilliseconds = retryPolicy(connectionFailures)
                    logger.warn { "Connection failure: Waiting $delayInMilliseconds ms to connect" }
                    delay(delayInMilliseconds)
                }

                try {
                    val socketSession = getSocketSession()

                    authenticate(socketSession).let {
                        if (it != AuthenticationStatus.AUTHENTICATED) {
                            // Failed to authenticate - this is not retryable
                            logger.error { "Authentication failed: $it" }
                            close()
                            return null
                        }
                    }

                    _connection = Connection(keyPair.public, socketSession) {}
                    connectionFailures = 0
                    logger.info { "Connection created to $uri for ${_connection!!.id}" }
                } catch (e: Exception) {
                    // TODO: Max connectFailures?
                    connectionFailures++
                    throw e
                }
            }

            if (_state == Opening)
                _state = Open

            return _connection
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

                    connect(false)?.also {
                        openMutex.unlock()
                        it.listen { payload -> handleMessage(messageHandler, payload) }
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

    suspend fun sendAdminMessage(message: AdminMessage) {
        require(_state == Open) { "Client must be open to send an admin message" }
        val body = ControlMessage(ControlMessageType.ADMIN, message.encode()).encodeProto()
        sendMessage(serverPublicKey!!, MessageStorageKey.none, body)
    }

    override suspend fun sendMessage(to: List<PublicKey>, key: MessageStorageKey, body: ByteArray) {
        val connection = withTimeout(connectTimeoutMilliseconds) { connect() }
            ?: throw ConnectException("Unable to connect to server")
        val message = Message(keyPair.public, body)

        // TODO: Should there be a limit on the size of messages?
        logger.info { "Sending message: $message" }

        // V1 does not support sending to multiple recipients in one payload, so multiple payload need to be supported - remove when V1 is deprecated
        encodePayload(to, key, message).forEach { payload ->
            withTimeout(requestTimeoutMilliseconds) {
                connection.writeSizedByteArray(payload)
            }
        }
    }

    private fun isControlMessage(message: Message): Boolean {
        return message.from == serverPublicKey
    }

    private suspend fun handleControlMessage(messageHandler: MessageHandler, message: Message) {
        val controlMessage = ControlMessage.decodeProto(message.body)

        when (controlMessage.type) {
            ControlMessageType.NONE -> {
                logger.error { "Received control message with type NONE" }
            }

            ControlMessageType.NO_PENDING_MESSAGES -> {
                eventHandler?.invoke(publicKey, RelayEvent.NO_PENDING_MESSAGES)
            }

            ControlMessageType.ADMIN -> {
                messageHandler(message.from, controlMessage.payload)
            }
        }
    }

    private suspend fun handleMessage(messageHandler: MessageHandler, payload: ByteArray) {
        try {
            val message = decodePayload(payload).decryptMessage(keyPair)
            logger.info { "Handling message: $message" }

            if (isControlMessage(message)) {
                handleControlMessage(messageHandler, message)
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