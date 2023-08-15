package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v1.Envelope
import io.opencola.relay.common.message.v2.MessageKey
import mu.KotlinLogging
import java.security.PublicKey

class MessageQueue(private val maxStoredBytes: Int) {
    private val logger = KotlinLogging.logger("MessageQueue")
    var bytesStored: Int = 0
        private set

    private val storedMessages = mutableListOf<StoredMessage>()

    fun addMessage(from: PublicKey, envelope: Envelope) {
        require(envelope.key.value != null)
        val senderSpecificKey = MessageKey.of(from.encoded.plus(envelope.key.value))
        val receiverId = Id.ofPublicKey(envelope.to)

        logger.info { "Adding message: Receiver=$receiverId, key=$senderSpecificKey" }

        val storedMessages = storedMessages
            .mapIndexed { index, storedMessage ->
                object {
                    val index = index
                    val storedMessage = storedMessage
                }
            }
            .filter { it.storedMessage.senderSpecificKey == senderSpecificKey }
        val existingMessageSize = storedMessages.sumOf { it.storedMessage.envelope.message.size }

        if (bytesStored + envelope.message.size - existingMessageSize > maxStoredBytes) {
            logger.info { "Message store for $receiverId is full - dropping message" }
            return
        }

        // Remove any existing messages from the same sender. We do this, rather than ignoring the message,
        // since newer messages with the same key may contain more recent data.
        val existingMessage = storedMessages.firstOrNull()
        val messageToStore = StoredMessage(senderSpecificKey, envelope)

        if (existingMessage != null) {
            bytesStored -= existingMessageSize
            this.storedMessages[existingMessage.index] = messageToStore
        } else
            this.storedMessages.add(messageToStore)

        bytesStored += envelope.message.size
    }

    fun getMessages(): Sequence<StoredMessage> {
        return storedMessages.asSequence()
    }

    fun removeMessage(key: MessageKey) {
        val matchingMessages = storedMessages.filter { it.senderSpecificKey == key }

        matchingMessages.forEach {
            val receiverId = Id.ofPublicKey(it.envelope.to)
            logger.info { "Removing message: Receiver=$receiverId, key=$key" }
        }

        storedMessages.removeAll(matchingMessages)
        bytesStored -= matchingMessages.sumOf { it.envelope.message.size }
    }
}