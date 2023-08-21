package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.v2.MessageStorageKey
import mu.KotlinLogging
import java.security.PublicKey

class MessageQueue(private val recipientPublicKey: PublicKey, private val maxStoredBytes: Int) {
    private val logger = KotlinLogging.logger("MessageQueue")
    var bytesStored: Int = 0
        private set

    private val storedMessages = mutableListOf<StoredMessage>()

    fun addMessage(from: PublicKey, to: PublicKey, envelope: Envelope) {
        require(to == recipientPublicKey)
        require(envelope.messageStorageKey != null)
        require(envelope.messageStorageKey.value != null)
        val receiverId = Id.ofPublicKey(to)

        logger.info { "Adding message: Receiver=$receiverId, key=$$envelope.messageStorageKey" }

        val senderSpecificKey = MessageStorageKey.of(from.encoded.plus(envelope.messageStorageKey.value))
        val storedMessages = storedMessages
            .mapIndexed { index, storedMessage ->
                object {
                    val index = index
                    val storedMessage = storedMessage
                }
            }
            .filter { it.storedMessage.senderSpecificKey == senderSpecificKey }
        val existingMessageSize = storedMessages.sumOf { it.storedMessage.envelope.message.bytes.size }

        if (bytesStored + envelope.message.bytes.size - existingMessageSize > maxStoredBytes) {
            logger.info { "Message store for $receiverId is full - dropping message" }
            return
        }

        // Remove any existing messages from the same sender. We do this, rather than ignoring the message,
        // since newer messages with the same key may contain more recent data.
        val existingMessage = storedMessages.firstOrNull()
        val messageToStore = StoredMessage(senderSpecificKey, to, envelope)

        if (existingMessage != null) {
            bytesStored -= existingMessageSize
            this.storedMessages[existingMessage.index] = messageToStore
        } else
            this.storedMessages.add(messageToStore)

        // TODO: This is over counting total memory usage, as a single envelope might be store multiple times
        bytesStored += envelope.message.bytes.size
    }

    fun getMessages(): Sequence<StoredMessage> {
        return storedMessages.asSequence()
    }

    fun removeMessage(key: MessageStorageKey) {
        val matchingMessages = storedMessages.filter { it.senderSpecificKey == key }

        matchingMessages.forEach {
            val receiverId = Id.ofPublicKey(it.to)
            logger.info { "Removing message: Receiver=$receiverId, key=$key" }
        }

        storedMessages.removeAll(matchingMessages)
        // TODO: This currently not accurate for total memory usage, as a single envelope might be store multiple times
        bytesStored -= matchingMessages.sumOf { it.envelope.message.bytes.size }
    }
}