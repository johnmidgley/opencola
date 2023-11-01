package io.opencola.relay.server

import io.opencola.model.Id
import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.*
import io.opencola.relay.common.message.*
import io.opencola.relay.common.message.v2.ControlMessage
import io.opencola.relay.common.message.v2.ControlMessageType
import io.opencola.relay.common.message.Message
import io.opencola.relay.common.message.v2.store.MemoryMessageStore
import io.opencola.relay.common.message.v2.store.MessageStore
import io.opencola.relay.common.message.v2.store.Usage
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.security.SecureRandom

abstract class AbstractRelayServer(
    protected val config: Config,
    protected val policyStore: PolicyStore,
    protected val connectionDirectory: ConnectionDirectory,
    protected val messageStore: MessageStore? = null,
) {
    val address = connectionDirectory.localAddress
    protected val serverKeyPair: KeyPair
    protected val logger = KotlinLogging.logger("RelayServer")
    protected val openMutex = Mutex(true)
    protected val random = SecureRandom()
    protected var state = Initialized
    protected var listenJob: Job? = null

    init {
        require(address.scheme == "ocr") { "Invalid scheme: ${address.scheme}" }
        serverKeyPair = config.security.keyPair
    }

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    fun localConnections(): Sequence<ConnectionEntry> {
        return connectionDirectory.getLocalConnections()
    }

    fun getUsage(): Sequence<Usage> {
        return (messageStore as? MemoryMessageStore)?.getUsage() ?: emptySequence()
    }

    protected abstract suspend fun authenticate(socketSession: SocketSession): PublicKey?

    // Decode the envelope into common server format
    protected abstract fun decodePayload(from: PublicKey, payload: ByteArray): Envelope

    // Turn the Envelope into a message that the client will understand
    protected abstract fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray

    private val noPendingMessagesMessage = Message(
        serverKeyPair.public,
        ControlMessage(ControlMessageType.NO_PENDING_MESSAGES).encodeProto()
    )

    private fun getQueueEmptyEnvelope(to: PublicKey): Envelope {
        return Envelope.from(serverKeyPair.private, to, null, noPendingMessagesMessage)
    }

    protected suspend fun sendStoredMessages(connection: Connection) {
        if (messageStore == null) return

        messageStore.consumeMessages(connection.id).forEach {
            val recipient = Recipient(connection.publicKey, it.secretKey)
            val envelope = Envelope(recipient, it.storageKey, it.message)
            connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
        }

        logger.info { "Queue empty for: ${connection.id}" }
        connection.writeSizedByteArray(encodePayload(connection.publicKey, getQueueEmptyEnvelope(connection.publicKey)))
    }

    suspend fun sendStoredMessages(id: Id) {
        if (messageStore == null) return
        val connectionEntry = connectionDirectory.get(id)

        if (connectionEntry?.connection == null) {
            connectionDirectory.remove(id)
            logger.warn { "Call to sendStoredMessages for non-local id: $id - removed from directory" }
        } else {
            sendStoredMessages(connectionEntry.connection!!)
        }
    }

    suspend fun handleSession(socketSession: SocketSession) {
        authenticate(socketSession)?.let { publicKey ->
            val connection = Connection(publicKey, socketSession) { connectionDirectory.remove(it.id) }
            val id = connection.id

            try {
                logger.info { "Session authenticated for: $id" }
                connectionDirectory.add(connection)
                // TODO: Policy is cached for connection lifetime. Should it expire or be invalidated on change?
                val policy = policyStore.getUserPolicy(id)!!
                sendStoredMessages(connection)

                // TODO: Add garbage collection on inactive connections?
                connection.listen { payload ->
                    if(payload.size > policy.messagePolicy.maxPayloadSize) {
                        logger.warn { "Payload too large from $id: Ignoring ${payload.size} bytes" }
                    } else {
                        handleMessage(publicKey, payload)
                    }
                }
            } finally {
                connection.close()
                connectionDirectory.remove(connection.id)
                logger.info { "Session closed for: $id" }
            }
        }
    }

    abstract suspend fun open()

    protected fun storeMessage(from: Id, to: Id, envelope: Envelope) {
        try {
            if (envelope.messageStorageKey != null) {
                val recipient = envelope.recipients.single { Id.ofPublicKey(it.publicKey) == to }
                messageStore?.addMessage(
                    from,
                    to,
                    envelope.messageStorageKey!!,
                    recipient.messageSecretKey,
                    envelope.message
                )
            }
        } catch (e: Exception) {
            logger.error { "Error while storing message - from: $from to: $to e: $e" }
        }
    }

    open suspend fun triggerRemoteDelivery(serverAddress: URI, to: Id) {
        throw NotImplementedError()
    }

    open suspend fun forwardMessage(
        serverAddress: URI,
        from: PublicKey,
        to: List<Id>,
        envelope: Envelope,
        payload: ByteArray
    ) {
        throw NotImplementedError()
    }

    private suspend fun deliverMessage(from: Id, to: RecipientConnection, envelope: Envelope) {
        require(from != to.recipient.id()) { "Attempt to deliver message to self" }
        val prefix = "from=$from, to=${to.recipient.id()}:"
        var messageDelivered = false
        val connectionEntry = to.connectionEntry

        try {
            if (connectionEntry == null) {
                logger.info { "$prefix no connection to receiver" }
            } else if (connectionEntry.connection != null) {
                val connection = connectionEntry.connection!!
                logger.info { "$prefix Delivering ${envelope.message.bytes.size} bytes" }
                connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
                messageDelivered = true
            } else {
                // This shouldn't happen. If there is a connectionEntry but no connection, then
                // the message should have been delivered remotely.
                to.recipient.id().let {
                    connectionDirectory.remove(it)
                    logger.error { "No connection for id ${it}: Removed connection" }
                }
            }
        } catch (e: Exception) {
            logger.error { "$prefix Error while handling message: $e" }
        } finally {
            if (!messageDelivered) {
                storeMessage(from, to.recipient.id(), envelope)
            }
        }
    }

    private suspend fun deliverLocalMessages(from: Id, to: List<RecipientConnection>, envelope: Envelope) {
        to.forEach {
            try {
                deliverMessage(from, it, envelope)
            } catch (e: Exception) {
                logger.error { "Error while delivering message from: $from to: $it - $e" }
            }
        }
    }

    private class RecipientConnection(val recipient: Recipient, val connectionEntry: ConnectionEntry?)

    private suspend fun deliverRemoteMessages(
        from: PublicKey,
        to: List<RecipientConnection>,
        envelope: Envelope,
        payload: ByteArray
    ) {
        require(to.all { it.connectionEntry?.address != null }) { "[deliverRemoteMessages] Invalid null address" }

        to
            .groupBy { it.connectionEntry!!.address }
            .forEach { (uri, recipients) ->
                forwardMessage(
                    uri,
                    from,
                    recipients.map { it.recipient.id() },
                    envelope,
                    payload
                )
            }
    }

    protected suspend fun handleMessage(from: PublicKey, payload: ByteArray, deliverRemoteMessages: Boolean = true) {
        val fromId = Id.ofPublicKey(from)

        try {
            decodePayload(from, payload).let { envelope ->
                logger.info { "Handling message from: $fromId to ${envelope.recipients.size} recipients" }
                val (localRecipients, remoteRecipients) = envelope.recipients
                    .map { RecipientConnection(it, connectionDirectory.get(it.id())) }
                    .partition { it.connectionEntry == null || it.connectionEntry.address == address }

                deliverLocalMessages(fromId, localRecipients, envelope)

                if(deliverRemoteMessages)
                    deliverRemoteMessages(from, remoteRecipients, envelope, payload)
            }
        } catch (e: Exception) {
            logger.error { "Error while handling message from $fromId: $e" }
        }
    }

    open suspend fun close() {
        state = Closed
        connectionDirectory.closeAll()
        listenJob?.cancel()
        listenJob = null
    }

    companion object {
        init {
            initProvider()
        }
    }
}