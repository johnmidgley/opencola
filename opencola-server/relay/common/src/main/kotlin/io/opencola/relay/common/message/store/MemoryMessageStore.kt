package io.opencola.relay.common.message.store

import io.opencola.model.Id
import io.opencola.relay.common.message.Envelope
import mu.KotlinLogging
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

class MemoryMessageStore(private val maxStoredBytesPerConnection: Long = 1024 * 1024 * 50) : MessageStore {
    private val logger = KotlinLogging.logger("MemoryMessageStore")
    private val messages = ConcurrentHashMap<PublicKey, MutableList<Envelope>>()

    private fun getUsage(messages: MutableList<Envelope>): Long {
        return messages.sumOf { it.message.size.toLong() }
    }

    // TODO: Shouldn't store messages that don't have key. Message bytes will change if we re-encrypt, so not stable for
    //  duplicate detection.
    override fun addMessage(envelope: Envelope) {
        logger.info { "Adding message to store: $envelope" }
        val bytesStored =  messages[envelope.to]?.let { getUsage(it) } ?: 0

        if(bytesStored + envelope.message.size > maxStoredBytesPerConnection) {
            logger.info { "Message store for ${Id.ofPublicKey(envelope.to)} is full - dropping message" }
            return
        }

        messages.compute(envelope.to) { _, currentList ->
            val list = currentList ?: mutableListOf()

            if(list.any { it.key.contentEquals(envelope.key) } ) {
                logger.info { "Ignoring duplicate message $envelope" }
                currentList
            } else {
                list.also { it.add(envelope) }
            }
        }
    }

    override fun getMessages(to: PublicKey): Sequence<Envelope> {
        return messages[to]?.asSequence() ?: emptySequence()
    }

    override fun removeMessage(envelope: Envelope) {
        logger.info { "Removing message from store: $envelope" }
        messages.compute(envelope.to) { _, currentList ->
            currentList?.filter { !it.key.contentEquals(envelope.key) }?.toMutableList()
        }
    }
}