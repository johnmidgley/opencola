package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50) : MessageStore {
    private val messageQueues = ConcurrentHashMap<Id, MessageQueue>()

    override fun addMessage(from: Id, to: Id, messageStorageKey: MessageStorageKey, messageSecretKey: EncryptedBytes, message: SignedBytes) {
        require(messageStorageKey != MessageStorageKey.none)
        require(messageStorageKey.value != null)

        messageQueues
            .getOrPut(to) { MessageQueue(to, maxStoredBytesPerConnection) }
            .apply { addMessage(StoredMessage(from, to, messageStorageKey, messageSecretKey, message)) }
    }

    override fun getMessages(to: Id): Sequence<StoredMessage> {
        return messageQueues[to]?.getMessages() ?: emptySequence()
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        messageQueues[storedMessage.to]?.removeMessage(storedMessage)
    }

    fun getUsage() : Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.bytesStored) }
    }
}