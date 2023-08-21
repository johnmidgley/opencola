package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.Envelope
import io.opencola.relay.common.message.v2.MessageStorageKey
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50) : MessageStore {
    private val messageQueues = ConcurrentHashMap<PublicKey, MessageQueue>()

    override fun addMessage(from: PublicKey, to: PublicKey, envelope: Envelope) {
        val messageStorageKey = envelope.messageStorageKey
        require(messageStorageKey != null)
        require(messageStorageKey != MessageStorageKey.none)
        require(messageStorageKey.value != null)

        messageQueues
            .getOrPut(to) { MessageQueue(to, maxStoredBytesPerConnection) }
            .apply { addMessage(from, to, envelope) }
    }

    override fun getMessages(to: PublicKey): Sequence<StoredMessage> {
        return messageQueues[to]?.getMessages() ?: emptySequence()
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        messageQueues[storedMessage.to]?.removeMessage(storedMessage.senderSpecificKey)
    }

    fun getUsage() : Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.bytesStored) }
    }
}