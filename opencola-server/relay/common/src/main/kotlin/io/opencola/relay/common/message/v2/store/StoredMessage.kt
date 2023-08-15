package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.v1.Envelope
import io.opencola.relay.common.message.v2.MessageKey


// Stored messages are used only for transport, and are de-duplicated by the senderSpecificKey. The message in the
// envelope is encrypted, and the same data encrypted twice will not have the same bytes, so it cannot be used for
// de-duplication.
// senderSpecificKeys are generated from the sender's public key and the message key. This allows
// for the same message from differing recipients to be distinguished.
data class StoredMessage(val senderSpecificKey: MessageKey, val envelope: Envelope) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StoredMessage

        return senderSpecificKey.equals(other.senderSpecificKey)
    }

    override fun hashCode(): Int {
        return senderSpecificKey.hashCode()
    }
}