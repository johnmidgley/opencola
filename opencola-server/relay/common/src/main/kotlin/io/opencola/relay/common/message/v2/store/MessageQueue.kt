package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import mu.KotlinLogging
import java.security.PublicKey
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock

class MessageQueue(private val recipientPublicKey: PublicKey, private val maxStoredBytes: Int) {
    private val logger = KotlinLogging.logger("MessageQueue")

    var bytesStored: Int = 0
        private set

    // QueuedMessage is used as a wrapper around StoredMessage to allow for de-duplication of messages with the same
    // senderSpecificKey, without having to recompute the sender specific key over and over again.
    data class QueuedMessage(val senderSpecificKey: MessageStorageKey, val storedMessage: StoredMessage)

    // We want to be able to replace items in the message queue, if a newer message with the same senderSpecificKey.
    // To do this, without having to copy the whole list on change, we simply use a mutable list and lock it when
    // operating on it
    private val lock = ReentrantLock()
    private val queuedMessages = mutableListOf<QueuedMessage>()

    fun addMessage(storedMessage: StoredMessage) {
        lock.lock()
        try {
            require(storedMessage.to.publicKey == recipientPublicKey)
            require(storedMessage.messageStorageKey.value != null)
            val receiverId = Id.ofPublicKey(storedMessage.to.publicKey)

            logger.info { "Adding message: $storedMessage" }

            val senderSpecificKey =
                MessageStorageKey.of(storedMessage.from.encoded.plus(storedMessage.messageStorageKey.value))
            val matchingMessages = queuedMessages
                .filter { it.senderSpecificKey == senderSpecificKey }
                .mapIndexed { index, matchingMessage ->
                    object {
                        val index = index
                        val matchingMessage = matchingMessage
                    }
                }
            val existingMessageSize = matchingMessages.sumOf { it.matchingMessage.storedMessage.message.bytes.size }

            if (bytesStored + storedMessage.message.bytes.size - existingMessageSize > maxStoredBytes) {
                logger.info { "Message store for $receiverId is full - dropping message" }
                return
            }

            // Remove any existing messages from the same sender. We do this, rather than ignoring the message,
            // since newer messages with the same key may contain more recent data.
            val existingMessage = matchingMessages.firstOrNull()
            val messageToStore = QueuedMessage(senderSpecificKey, storedMessage)

            if (existingMessage != null) {
                bytesStored -= existingMessageSize
                this.queuedMessages[existingMessage.index] = messageToStore
            } else
                this.queuedMessages.add(messageToStore)

            // TODO: This is over counting TOTAL memory usage, as a single message to multiple receivers will be referenced multiple times
            bytesStored += storedMessage.message.bytes.size
        } finally {
            lock.unlock()
        }
    }

    fun getMessages(): Sequence<StoredMessage> {
        lock.lock()
        try {
            return queuedMessages.map { it.storedMessage }.asSequence()
        } finally {
            lock.unlock()
        }
    }

    fun removeMessage(id: UUID) {
        lock.lock()
        try {
            val matchingMessages = queuedMessages.filter { it.storedMessage.id == id }

            matchingMessages.forEach {
                logger.info { "Removing message: ${it.storedMessage}" }
            }

            queuedMessages.removeAll { it.storedMessage.id == id }

            // TODO: This currently not accurate for TOTAL memory usage, as a single message to multiple receivers will be referenced multiple times
            bytesStored -= matchingMessages.sumOf { it.storedMessage.message.bytes.size }
        } finally {
            lock.unlock()
        }
    }
}