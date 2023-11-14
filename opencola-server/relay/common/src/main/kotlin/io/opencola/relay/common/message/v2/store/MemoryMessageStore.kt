package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val maxBytesStored: Long, private val policyStore: PolicyStore) : MessageStore {
    private val logger = KotlinLogging.logger("MemoryMessageStore")
    private val messageQueues = ConcurrentHashMap<Id, MessageQueue>()

    private fun getBytesStored(): Long {
        return messageQueues.values.sumOf { it.bytesStored }
    }

    override fun addMessage(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        message: SignedBytes
    ) {
        require(storageKey != MessageStorageKey.none)
        require(storageKey.value != null)

        // TODO: This fetches the policy on every message. Would it be better to cache it for the life of the connection?
        val storagePolicy = policyStore.getUserPolicy(to, to)?.storagePolicy

        if (storagePolicy == null) {
            logger.warn { "No storage policy for $to - dropping message" }
            return
        }

        val bytesAvailable = maxBytesStored - getBytesStored()

        messageQueues
            .getOrPut(to) { MessageQueue(to, storagePolicy.maxStoredBytes) }
            .apply { addMessage(bytesAvailable, StoredMessage(from, to, storageKey, secretKey, message)) }
    }

    override fun getMessages(to: Id, limit: Int): List<StoredMessage> {
        return messageQueues[to]?.getMessages()?.take(limit) ?: emptyList()
    }

    override fun removeMessage(header: StoredMessageHeader) {
        messageQueues[header.to]?.removeMessage(header)
    }

    override fun removeMessages(maxAgeMilliseconds: Long, limit: Int): List<StoredMessageHeader> {
        return messageQueues.values.flatMap { it.removeMessages(maxAgeMilliseconds, limit) }
    }

    override fun getUsage(): Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.numMessages, it.value.bytesStored) }
    }
}