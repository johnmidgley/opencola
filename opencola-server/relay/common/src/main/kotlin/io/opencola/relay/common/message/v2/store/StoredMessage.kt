package io.opencola.relay.common.message.v2.store

import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey


// Stored messages are used only for transport, and are de-duplicated by the senderSpecificKey. The message in the
// envelope is encrypted, and the same data encrypted twice will not have the same bytes, so it cannot be used for
// de-duplication.
// StoredMessages are considered equal if they have the same senderSpecificKey.
// senderSpecificKeys are generated from the sender's public key and the message key. This allows
// for the same message from differing recipients to be distinguished.
data class StoredMessage(val senderSpecificKey: MessageStorageKey, val to: Recipient, val message: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredMessage) return false
        if (other.to.publicKey != to.publicKey) return false
        return senderSpecificKey == other.senderSpecificKey
    }

    override fun hashCode(): Int {
        return senderSpecificKey.hashCode()
    }
}