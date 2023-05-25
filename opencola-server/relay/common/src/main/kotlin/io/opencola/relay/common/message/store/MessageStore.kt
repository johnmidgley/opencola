package io.opencola.relay.common.message.store

import io.opencola.relay.common.message.Envelope
import java.security.PublicKey

interface MessageStore {
    fun addMessage(envelope: Envelope)
    fun getMessages(to: PublicKey): Sequence<Envelope>
    fun removeMessage(envelope: Envelope)
}