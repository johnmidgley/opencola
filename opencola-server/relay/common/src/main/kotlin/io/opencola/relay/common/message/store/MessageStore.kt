package io.opencola.relay.common.message.store

import io.opencola.relay.common.message.Envelope
import java.security.PublicKey

interface MessageStore {
    fun addMessage(from: PublicKey, envelope: Envelope)
    fun getMessages(to: PublicKey): Sequence<StoredMessage>
    fun removeMessage(storedMessage: StoredMessage)
}