package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import mu.KotlinLogging
import java.security.PublicKey

class MessageQueue(private val recipientPublicKey: PublicKey, private val maxStoredBytes: Int) {
    private val logger = KotlinLogging.logger("MessageQueue")
    var bytesStored: Int = 0
        private set

    private val storedMessages = mutableListOf<StoredMessage>()

    fun addMessage(to: Recipient, messageStorageKey: MessageStorageKey, message: ByteArray) {
        require(to.publicKey == recipientPublicKey)
        require(messageStorageKey.value != null)
        val receiverId = Id.ofPublicKey(to.publicKey)

        logger.info { "Adding message: Receiver=$receiverId, key=$$messageStorageKey" }

        val storedMessages = storedMessages
            .mapIndexed { index, storedMessage ->
                object {
                    val index = index
                    val storedMessage = storedMessage
                }
            }
            .filter { it.storedMessage.senderSpecificKey == messageStorageKey }
        val existingMessageSize = storedMessages.sumOf { it.storedMessage.message.size }

        if (bytesStored + message.size - existingMessageSize > maxStoredBytes) {
            logger.info { "Message store for $receiverId is full - dropping message" }
            return
        }

        // Remove any existing messages from the same sender. We do this, rather than ignoring the message,
        // since newer messages with the same key may contain more recent data.
        val existingMessage = storedMessages.firstOrNull()
        val messageToStore = StoredMessage(messageStorageKey, to, message)

        if (existingMessage != null) {
            bytesStored -= existingMessageSize
            this.storedMessages[existingMessage.index] = messageToStore
        } else
            this.storedMessages.add(messageToStore)

        bytesStored += message.size
    }

    fun getMessages(): Sequence<StoredMessage> {
        return storedMessages.asSequence()
    }

    fun removeMessage(key: MessageStorageKey) {
        val matchingMessages = storedMessages.filter { it.senderSpecificKey == key }

        matchingMessages.forEach {
            val receiverId = Id.ofPublicKey(it.to.publicKey)
            logger.info { "Removing message: Receiver=$receiverId, key=$key" }
        }

        storedMessages.removeAll(matchingMessages)
        bytesStored -= matchingMessages.sumOf { it.message.size }
    }
}