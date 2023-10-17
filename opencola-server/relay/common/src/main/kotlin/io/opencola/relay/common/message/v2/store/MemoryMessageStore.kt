package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.SignedBytes
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50) : MessageStore {
    private val messageQueues = ConcurrentHashMap<PublicKey, MessageQueue>()

    override fun addMessage(from: PublicKey, to: Recipient, messageStorageKey: MessageStorageKey, message: SignedBytes) {
        require(messageStorageKey != MessageStorageKey.none)
        require(messageStorageKey.value != null)

        messageQueues
            .getOrPut(to.publicKey) { MessageQueue(to.publicKey, maxStoredBytesPerConnection) }
            .apply { addMessage(StoredMessage(from, to, messageStorageKey, message)) }
    }

    override fun getMessages(to: PublicKey): Sequence<StoredMessage> {
        return messageQueues[to]?.getMessages() ?: emptySequence()
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        messageQueues[storedMessage.to.publicKey]?.removeMessage(storedMessage.id)
    }

    fun getUsage() : Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.bytesStored) }
    }
}