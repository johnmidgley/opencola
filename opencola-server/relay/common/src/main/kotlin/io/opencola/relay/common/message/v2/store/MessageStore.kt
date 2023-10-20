package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes

interface MessageStore {
    fun addMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        message: SignedBytes
    )

    fun getMessages(to: Id, limit: Int = 10): List<StoredMessage>
    fun removeMessage(storedMessage: StoredMessage)

    // Convenient way to consume messages that only removes a message when the next one (or end) is accessed
    fun consumeMessages(id: Id, batchSize: Int = 10) : Sequence<StoredMessage> {
        return sequence {
            var previousMessage: StoredMessage? = null

            do {
                if(previousMessage != null) {
                    removeMessage(previousMessage)
                    previousMessage = null
                }

                val messages = getMessages(id, batchSize)

                messages.forEach{
                    if(previousMessage != null) {
                        removeMessage(previousMessage!!)
                        previousMessage = null
                    }
                    yield(it)
                    previousMessage = it
                }
            } while (messages.size == batchSize)

            if(previousMessage != null) {
                removeMessage(previousMessage!!)
            }
        }
    }

    fun drainMessages(to: Id, batchSize: Int = 10) {
        consumeMessages(to, batchSize).forEach { _ -> }
    }
}