package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import mu.KotlinLogging
import java.util.concurrent.locks.ReentrantLock

class MessageQueue(private val recipientId: Id, private val maxStoredBytes: Int) {
    private val logger = KotlinLogging.logger("MessageQueue")

    var bytesStored: Int = 0
        private set

    val numMessages: Int
        get() = queuedMessages.size

    // We want to be able to replace items in the message queue, if a newer message with the same senderSpecificKey.
    // To do this, without having to copy the whole list on change, we simply use a mutable list and lock it when
    // operating on it
    // TODO: Use coroutines equivalent - this will cause unnecessary contention
    private val lock = ReentrantLock()
    private val queuedMessages = mutableListOf<StoredMessage>()

    fun addMessage(storedMessage: StoredMessage) {
        lock.lock()
        try {
            require(storedMessage.to == recipientId)
            require(storedMessage.storageKey.value != null)

            logger.info { "Adding message: $storedMessage" }

            val matchingMessages = queuedMessages
                .filter { it.matches(storedMessage) }
                .mapIndexed { index, matchingMessage ->
                    object {
                        val index = index
                        val matchingMessage = matchingMessage
                    }
                }

            // This slightly under-counts usage, as it only counts the bytes inside the signed message, not including
            // the signature. Fixing this would be inefficient, as we'd need to encode the signed message to get its
            // size, which isn't worth it.
            val existingMessageSize = matchingMessages.sumOf { it.matchingMessage.message.bytes.size }

            if (bytesStored + storedMessage.message.bytes.size - existingMessageSize > maxStoredBytes) {
                logger.info { "Message store for $recipientId is full - dropping message" }
                return
            }

            if(matchingMessages.count() > 1) {
                logger.error { "Multiple messages with the same senderSpecificKey in the message queue for $recipientId" }
            }

            // Replace existing message from the same sender. We do this, rather than ignoring the message,
            // since newer messages with the same key may contain more recent data.
            val existingMessage = matchingMessages.firstOrNull()

            if (existingMessage != null) {
                bytesStored -= existingMessageSize
                this.queuedMessages[existingMessage.index] = storedMessage
            } else
                this.queuedMessages.add(storedMessage)

            // TODO: This is over counting TOTAL memory usage, as a single message to multiple receivers will be referenced multiple times
            bytesStored += storedMessage.message.bytes.size
        } finally {
            lock.unlock()
        }
    }

    fun getMessages(): List<StoredMessage> {
        lock.lock()
        try {
            return queuedMessages
        } finally {
            lock.unlock()
        }
    }

    fun removeMessage(storedMessage: StoredMessage) {
        lock.lock()
        try {
            val matchingMessages =
                queuedMessages.filter { it.matches(storedMessage) }

            matchingMessages.forEach {
                logger.info { "Removing message: ${it}" }
            }

            queuedMessages.removeAll(matchingMessages)

            // TODO: This currently not accurate for TOTAL memory usage, as a single message to multiple receivers will be referenced multiple times
            bytesStored -= matchingMessages.sumOf { it.message.bytes.size }
        } finally {
            lock.unlock()
        }
    }
}