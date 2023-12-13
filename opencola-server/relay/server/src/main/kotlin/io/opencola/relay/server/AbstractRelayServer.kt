package io.opencola.relay.server

import io.opencola.event.log.EventLogger
import io.opencola.event.log.EventLoggerWrapper
import io.opencola.io.readFileBlocks
import io.opencola.model.Id
import io.opencola.relay.common.State.*
import io.opencola.relay.common.connection.*
import io.opencola.relay.common.message.*
import io.opencola.relay.common.message.v2.*
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
import java.security.PublicKey
import java.security.SecureRandom
import kotlin.concurrent.thread

abstract class AbstractRelayServer(
    protected val config: RelayConfig,
    eventLogger: EventLogger,
    protected val policyStore: PolicyStore,
    protected val connectionDirectory: ConnectionDirectory,
    protected val messageStore: MessageStore? = null,
) {
    val address = connectionDirectory.localAddress
    protected val serverKeyPair = config.security.keyPair
    protected val rootId = config.security.rootId
    protected val logger = KotlinLogging.logger("RelayServer")
    protected val event = EventLoggerWrapper(eventLogger, logger)
    protected val openMutex = Mutex(true)
    protected val random = SecureRandom()
    protected var state = Initialized
    protected var listenJob: Job? = null

    init {
        require(address.scheme == "ocr") { "Invalid scheme: ${address.scheme}" }
    }

    suspend fun waitUntilOpen() {
        openMutex.withLock { }
    }

    fun localConnections(): Sequence<ConnectionEntry> {
        return connectionDirectory.getConnections()
    }

    fun getUsage(): Sequence<Usage> {
        return (messageStore as? MemoryMessageStore)?.getUsage() ?: emptySequence()
    }

    protected abstract suspend fun authenticate(socketSession: SocketSession): Connection?

    // Decode the envelope into common server format
    protected abstract fun decodePayload(from: PublicKey, payload: ByteArray): Envelope

    // Turn the Envelope into a message that the client will understand
    protected abstract fun encodePayload(to: PublicKey, envelope: Envelope): ByteArray

    private val noPendingMessagesMessage = Message(
        serverKeyPair.public,
        ControlMessage(ControlMessageType.NO_PENDING_MESSAGES).encodeProto()
    )

    // TODO: Needed? or unfold into sendQueueEmptyMessage?
    private fun getQueueEmptyEnvelope(to: PublicKey): Envelope {
        return Envelope.from(serverKeyPair.private, to, null, noPendingMessagesMessage)
    }

    private suspend fun sendQueueEmptyMessage(connection: Connection) {
        // Only send queue empty message if there is a message store, since otherwise, there isn't a queue (i.e. V1).
        if (messageStore != null)
            connection.writeSizedByteArray(
                encodePayload(
                    connection.publicKey,
                    getQueueEmptyEnvelope(connection.publicKey)
                )
            )
    }

    private suspend fun sendStoredMessages(connection: Connection): Int {
        var sentMessageCount = 0

        messageStore?.consumeMessages(connection.id)?.forEach {
            val recipient = Recipient(connection.publicKey, it.header.secretKey)
            val envelope = Envelope(recipient, it.header.storageKey, it.body)
            connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
            sentMessageCount++
        }

        return sentMessageCount
    }

    suspend fun sendStoredMessages(id: Id) {
        if (messageStore == null) return
        val connectionEntry = connectionDirectory.get(id)

        if (connectionEntry?.connection == null) {
            connectionDirectory.remove(id)
            event.warn("SendStoredMessagesForNonLocalId") { "Call to sendStoredMessages for non-local id: $id - removed from directory" }
        } else {
            sendStoredMessages(connectionEntry.connection!!)
        }
    }

    suspend fun handleSession(socketSession: SocketSession) {
        if (connectionDirectory.size() >= config.capacity.maxConnections) {
            event.warn("MaxConnections") { "Max connections reached: ${config.capacity.maxConnections}" }
            socketSession.close()
            return
        }

        authenticate(socketSession)?.let { connection ->
            val id = connection.id

            try {
                event.info("SessionStarted") { "Session started: $id" }
                // TODO: Policy is cached for connection lifetime. Should it expire or be invalidated on change?
                val policy = policyStore.getUserPolicy(id, id)!!
                sendStoredMessages(connection)
                sendQueueEmptyMessage(connection)

                // TODO: Add garbage collection on inactive connections?
                connection.listen { payload ->
                    if (payload.size > policy.messagePolicy.maxPayloadSize) {
                        event.warn("PayloadTooLarge") { "Payload too large from $id: Ignoring ${payload.size} bytes" }
                    } else {
                        handleMessage(connection.publicKey, payload)
                        if (sendStoredMessages(connection) > 0)
                            event.warn("SentStoredMessagesOnHandleMessage") { "Stored messages sent on handleMessage to $id" }
                    }
                }
            } catch (e: Exception) {
                event.error("SessionError") { "Error while handling session: $e" }
            } finally {
                connection.close()
                event.info("SessionStopped") { "Session stopped: $id" }
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
            event.error("StoreMessageError") { "Error while storing message - from: $from to: $to e: $e" }
        }
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

            } else if (connectionEntry.connection == null) {
                // This shouldn't happen. If there is a connectionEntry but no connection, then
                // the message should have been delivered remotely.
                to.recipient.id().let {
                    connectionDirectory.remove(it)
                    event.error("LocalNullConnection") { "No connection for id ${it}: Removed connection" }
                }
            } else {
                val connection = connectionEntry.connection!!
                logger.info { "$prefix Delivering ${envelope.message.bytes.size} bytes" }
                connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
                messageDelivered = true

                if (sendStoredMessages(connection) > 0)
                    event.warn("SentStoredMessagesOnDeliverMessage") { "Stored messages sent on deliverMessage to ${to.recipient.id()}" }
            }
        } catch (e: Exception) {
            event.error("DeliverMessageError") { "$prefix Error while delivering message: $e" }
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
                event.error("DeliverMessageError") { "Error while delivering message from: $from to: $it - $e" }
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

    private suspend fun sendAdminMessage(to: Id, adminMessage: AdminMessage) {
        val connection = connectionDirectory.get(to)?.connection
            ?: throw IllegalStateException("No connection for $to - can't send command response")

        val controlMessage = ControlMessage(ControlMessageType.ADMIN, adminMessage.encode())
        val message = Message(serverKeyPair.public, controlMessage.encodeProto())
        val envelope = Envelope.from(serverKeyPair.private, connection.publicKey, null, message)
        connection.writeSizedByteArray(encodePayload(connection.publicKey, envelope))
    }

    private fun authorizeAdmin(id: Id) {
        if (id != config.security.rootId) {
            val policy = policyStore.getUserPolicy(id, id)
            require(policy != null) { "No policy for $id" }
            require(policy.adminPolicy.isAdmin) { "$id is not admin" }
        }
    }

    private suspend fun executeCommand(execCommand: ExecCommand): AdminMessage {
        val response = CompletableDeferred<AdminMessage>()

        thread {
            // TODO: Handle timeout
            val result = try {
                val command = "cd ${execCommand.workingDir};${execCommand.command}; pwd"
                // TODO: Make shell configurable
                val process = Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c") + command)
                val stdout = process.inputStream.bufferedReader().readText().trimEnd { it == '\n' }
                val stderr = process.errorStream.bufferedReader().readText().trimEnd { it == '\n' }
                val lines = stdout.lines()
                val workingDir = lines.last() // Last line is always resulting working directory because of ; pwd
                val cmdOut = lines.take(lines.size - 1).joinToString("\n")
                ExecCommandResponse(execCommand.id, workingDir, cmdOut, stderr)
            } catch (e: Exception) {
                CommandResponse(execCommand.id, Status.FAILURE, State.COMPLETE, e.message)
            }
            response.complete(result)
        }

        return response.await()
    }

    private suspend fun sendFile(from: Id, getFileCommand: GetFileCommand) : AdminMessage {
        try {
            withContext(Dispatchers.IO) {
                readFileBlocks(getFileCommand.path).forEach {
                    sendAdminMessage(from, GetFileBlockResponse(getFileCommand.id, it))
                }
            }
        } catch (e: Exception) {
            return CommandResponse(getFileCommand.id, Status.FAILURE, State.COMPLETE, e.message)
        }

        return CommandResponse(getFileCommand.id, Status.SUCCESS, State.COMPLETE, "File sent")
    }

    private suspend fun handleAdminMessage(fromId: Id, adminMessage: AdminMessage) {
        try {
            logger.info { "Handling command: $fromId $adminMessage" }
            val response = when (adminMessage) {
                is SetPolicyCommand -> {
                    val policy = adminMessage.policy
                    policyStore.setPolicy(fromId, policy)
                    CommandResponse(adminMessage.id, Status.SUCCESS, State.COMPLETE, "Policy \"${policy.name}\" set")
                }

                is GetPolicyCommand -> {
                    GetPolicyResponse(adminMessage.id, policyStore.getPolicy(fromId, adminMessage.name))
                }

                is GetPoliciesCommand -> {
                    GetPoliciesResponse(adminMessage.id, policyStore.getPolicies(fromId).toList())
                }

                is RemovePolicyCommand -> {
                    policyStore.removePolicy(fromId, adminMessage.name)
                    CommandResponse(
                        adminMessage.id,
                        Status.SUCCESS,
                        State.COMPLETE,
                        "Policy \"${adminMessage.name}\" removed"
                    )
                }

                is SetUserPolicyCommand -> {
                    policyStore.setUserPolicy(fromId, adminMessage.userId, adminMessage.policyName)
                    CommandResponse(
                        adminMessage.id,
                        Status.SUCCESS,
                        State.COMPLETE,
                        "User ${adminMessage.userId} policy set to \"${adminMessage.policyName}\""
                    )
                }

                is GetUserPolicyCommand -> {
                    GetUserPolicyResponse(adminMessage.id, policyStore.getUserPolicy(fromId, adminMessage.userId))
                }

                is GetUserPoliciesCommand -> {
                    GetUserPoliciesResponse(adminMessage.id, policyStore.getUserPolicies(fromId).toList())
                }

                is RemoveUserPolicyCommand -> {
                    policyStore.removeUserPolicy(fromId, adminMessage.userId)
                    CommandResponse(
                        adminMessage.id,
                        Status.SUCCESS,
                        State.COMPLETE,
                        "User ${adminMessage.userId} policy removed"
                    )
                }

                is GetConnectionsCommand -> {
                    val connectionInfos = connectionDirectory.getConnections()
                        .map {
                            // Indicate whether there's an active connection from this server with a *
                            val addressSuffix = it.connection?.let { "*" } ?: ""
                            ConnectionInfo(it.id, it.address.toString() + addressSuffix, it.connectTimeMilliseconds)
                        }
                        .toList()
                    GetConnectionsResponse(adminMessage.id, connectionInfos)
                }

                is GetMessagesCommand -> {
                    val messages = messageStore?.getMessages(null)?.map { MessageInfo(it) }?.toList() ?: emptyList()
                    GetMessagesResponse(adminMessage.id, messages)
                }

                is RemoveUserMessagesCommand -> {
                    val headers = messageStore?.removeMessages(adminMessage.userId)
                    var deletedCount = 0

                    headers?.forEach {
                        sendAdminMessage(
                            fromId,
                            CommandResponse(adminMessage.id, Status.SUCCESS, State.PENDING, "Removed $it")
                        )
                        deletedCount++
                    }
                    CommandResponse(adminMessage.id, Status.SUCCESS, State.COMPLETE, "Removed $deletedCount messages")
                }

                is RemoveMessagesByAgeCommand -> {
                    if (messageStore != null) {
                        val headers = messageStore.removeMessages(adminMessage.maxAgeMilliseconds)
                        var deletedCount = 0

                        headers.forEach {
                            sendAdminMessage(
                                fromId,
                                CommandResponse(adminMessage.id, Status.SUCCESS, State.PENDING, "Removed $it")
                            )
                            deletedCount++
                        }

                        CommandResponse(
                            adminMessage.id,
                            Status.SUCCESS,
                            State.COMPLETE,
                            "Removed $deletedCount messages"
                        )
                    } else {
                        CommandResponse(adminMessage.id, Status.FAILURE, State.COMPLETE, "No message store")
                    }
                }

                is GetMessageUsageCommand -> {
                    GetMessageUsageResponse(adminMessage.id, messageStore?.getUsage()?.toList() ?: emptyList())
                }

                is ExecCommand -> {
                    executeCommand(adminMessage)
                }

                is GetFileCommand -> {
                    sendFile(fromId, adminMessage)
                }

                else -> {
                    "Unknown command: $adminMessage".let {
                        event.error("UnknownCommand") { it }
                        CommandResponse(adminMessage.id, Status.FAILURE, State.COMPLETE, it)
                    }
                }
            }

            sendAdminMessage(fromId, response)
        } catch (e: Exception) {
            event.error("HandleCommandError") { "Error while handling command: $e" }
            sendAdminMessage(fromId, CommandResponse(adminMessage.id, Status.FAILURE, State.COMPLETE, e.message))
        }
    }

    private suspend fun handleControlMessage(fromId: Id, envelope: Envelope) {
        authorizeAdmin(fromId)

        val recipient = envelope.recipients.singleOrNull()
        if (recipient == null || recipient.publicKey != serverKeyPair.public) {
            event.warn("InvalidCommandRecipient") { "Invalid command recipient: $recipient" }
            return
        }

        val controlMessage = ControlMessage.decodeProto(envelope.decryptMessage(serverKeyPair).body)

        when (controlMessage.type) {
            ControlMessageType.NONE -> {
                logger.error { "Received control message with type NONE" }
            }

            ControlMessageType.NO_PENDING_MESSAGES -> {
                event.error("NoPendingMessages") { "Server received unexpected NO_PENDING_MESSAGES message" }
            }

            ControlMessageType.ADMIN -> {
                handleAdminMessage(fromId, AdminMessage.decode(controlMessage.payload))
            }
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

                if (envelope.recipients.singleOrNull()?.publicKey == serverKeyPair.public) {
                    handleControlMessage(fromId, envelope)
                } else {
                    deliverLocalMessages(fromId, localRecipients, envelope)

                    if (deliverRemoteMessages)
                        deliverRemoteMessages(from, remoteRecipients, envelope, payload)
                }
            }
        } catch (e: Exception) {
            event.error("HandleMessageError") { "Error while handling message from $fromId: $e" }
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