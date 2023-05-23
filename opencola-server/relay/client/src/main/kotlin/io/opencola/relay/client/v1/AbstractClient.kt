package io.opencola.relay.client.v1

import io.opencola.model.Id
import io.opencola.security.*
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

const val defaultOCRPort = 2652

abstract class AbstractClient(
    protected val uri: URI,
    private val keyPair: KeyPair,
    final override val name: String? = null,
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

    override val publicKey: PublicKey
        get() = keyPair.public

    override val state: State
        get() = _state

    override suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    // Should only be called once, right after connection to server
    private suspend fun authenticate(socketSession: SocketSession) {
        // Send public key
        logger.debug { "Sending public key" }
        socketSession.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        logger.debug { "Reading challenge" }
        val challengeBytes = socketSession.readSizedByteArray()

        // Sign challenge and send back
        logger.debug { "Signing challenge" }
        socketSession.writeSizedByteArray(sign(keyPair.private, challengeBytes).bytes)

        val authenticationResponse = IntByteArrayCodec.decode(socketSession.readSizedByteArray())
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }

        logger.debug { "Authenticated" }
    }

    abstract suspend fun getSocketSession(): SocketSession

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

    private suspend fun handleMessage(
        payload: ByteArray,
        handler: suspend (from: PublicKey, message: ByteArray) -> Unit
    ) {
        try {
            logger.info { "Handling message" }
            val message = Message.decode(decrypt(keyPair.private, payload)).validate()
            handler(message.header.from, message.body)
        } catch (e: Exception) {
            logger.error { "Exception in handleMessage: $e" }
        }
    }

    override suspend fun open(messageHandler: suspend (from: PublicKey, message: ByteArray) -> Unit) = coroutineScope {
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

    override suspend fun close() {
        _state = Closed
        listenJob?.cancel()
        listenJob = null
        _connection?.close()
        _connection = null
        logger.info { "Closed - $name" }
    }

    override suspend fun sendMessage(to: PublicKey, body: ByteArray) {
        val message = Message(keyPair, UUID.randomUUID(), body)
        val envelope = Envelope(to, null, message)

        try {
            // TODO: Should there be a limit on the size of messages?
            logger.info { "Sending message: ${message.header}" }
            withTimeout(requestTimeoutMilliseconds) {
                getConnection().writeSizedByteArray(Envelope.encode(envelope))
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

    companion object {
        init {
            initProvider()
        }
    }
}