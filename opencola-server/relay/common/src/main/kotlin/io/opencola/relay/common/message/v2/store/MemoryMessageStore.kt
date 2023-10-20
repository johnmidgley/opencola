package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50) : MessageStore {
    private val messageQueues = ConcurrentHashMap<Id, MessageQueue>()

    override fun addMessage(from: Id, to: Id, storageKey: MessageStorageKey, secretKey: EncryptedBytes, message: SignedBytes) {
        require(storageKey != MessageStorageKey.none)
        require(storageKey.value != null)

        messageQueues
            .getOrPut(to) { MessageQueue(to, maxStoredBytesPerConnection) }
            .apply { addMessage(StoredMessage(from, to, storageKey, secretKey, message)) }
    }

    override fun getMessages(to: Id, limit: Int): List<StoredMessage> {
        return messageQueues[to]?.getMessages()?.take(limit) ?: emptyList()
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        messageQueues[storedMessage.to]?.removeMessage(storedMessage)
    }

    fun getUsage() : Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.bytesStored) }
    }
}