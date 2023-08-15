package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.v1.Envelope
import io.opencola.relay.common.message.v2.MessageKey
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

class MemoryMessageStore(private val maxStoredBytesPerConnection: Int = 1024 * 1024 * 50) : MessageStore {
    private val messageQueues = ConcurrentHashMap<PublicKey, MessageQueue>()

    // TODO: Shouldn't store messages that don't have key. Message bytes will change if we re-encrypt, so not stable for
    //  duplicate detection.
    override fun addMessage(from: PublicKey, envelope: Envelope) {
        require(envelope.key != MessageKey.none)
        messageQueues.getOrPut(envelope.to) { MessageQueue(maxStoredBytesPerConnection) }
            .apply { addMessage(from, envelope) }

    }

    override fun getMessages(to: PublicKey): Sequence<StoredMessage> {
        return messageQueues[to]?.getMessages() ?: emptySequence()
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        messageQueues[storedMessage.envelope.to]?.removeMessage(storedMessage.senderSpecificKey)
    }

    fun getUsage() : Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.bytesStored) }
    }
}