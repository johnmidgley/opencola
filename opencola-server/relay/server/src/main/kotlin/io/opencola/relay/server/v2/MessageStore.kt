package io.opencola.relay.server.v2

import io.opencola.relay.common.Envelope
import java.security.PublicKey

interface MessageStore {
    fun addMessage(envelope: Envelope)
    fun getMessages(to: PublicKey): Sequence<ByteArray>
    fun removeMessage(envelope: Envelope)
}