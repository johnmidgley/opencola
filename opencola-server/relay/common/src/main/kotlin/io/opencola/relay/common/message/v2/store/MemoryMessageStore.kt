package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.common.policy.PolicyStore
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

// TODO: Add global memory limit
class MemoryMessageStore(private val policyStore: PolicyStore) : MessageStore {
    private val logger = KotlinLogging.logger("MemoryMessageStore")
    private val messageQueues = ConcurrentHashMap<Id, MessageQueue>()

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

        messageQueues
            .getOrPut(to) { MessageQueue(to, storagePolicy.maxStoredBytes) }
            .apply { addMessage(StoredMessage(from, to, storageKey, secretKey, message)) }
    }

    override fun getMessages(to: Id, limit: Int): List<StoredMessage> {
        return messageQueues[to]?.getMessages()?.take(limit) ?: emptyList()
    }

    override fun removeMessage(storedMessage: StoredMessage) {
        messageQueues[storedMessage.to]?.removeMessage(storedMessage)
    }

    override fun getUsage(): Sequence<Usage> {
        return messageQueues.entries.asSequence().map { Usage(it.key, it.value.bytesStored) }
    }
}