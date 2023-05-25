package io.opencola.relay.server.v2

import io.opencola.model.Id
import io.opencola.relay.common.Envelope
import mu.KotlinLogging
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

class MemoryMessageStore : MessageStore {
    private val logger = KotlinLogging.logger("MemoryMessageStore")
    private val messages = ConcurrentHashMap<PublicKey, MutableList<Envelope>>()

    override fun addMessage(envelope: Envelope) {
        messages.compute(envelope.to) { publicKey, currentList ->
            val list = currentList ?: mutableListOf()

            if(list.any { it.key.contentEquals(envelope.key) } ) {
                logger.info { "Ignoring duplicate message for ${Id.ofPublicKey(publicKey)} - key ${envelope.key}" }
                currentList
            } else {
                list.also { it.add(envelope) }
            }
        }
    }

    override fun getMessages(to: PublicKey): Sequence<ByteArray> {
        return messages[to]?.asSequence()?.map { it.message } ?: emptySequence()
    }

    override fun removeMessage(envelope: Envelope) {
        messages.compute(envelope.to) { _, currentList ->
            currentList?.filter { !it.key.contentEquals(envelope.key) }?.toMutableList()
        }
    }
}