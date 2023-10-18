package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.SignedBytes
import java.security.PublicKey

data class StoredMessage (
    val from: PublicKey,
    val to: Recipient,
    val messageStorageKey: MessageStorageKey,
    val message: SignedBytes
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredMessage) return false

        if (from != other.from) return false
        if (to != other.to) return false
        if (messageStorageKey != other.messageStorageKey) return false
        if (message != other.message) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + messageStorageKey.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }

    override fun toString(): String {
        return "StoredMessage(from=${Id.ofPublicKey(from)}, to=$to, messageStorageKey=$messageStorageKey, message=${message.bytes.size} bytes)"
    }

    // Stored messages match if they have the same from, to, and messageStorageKey. This is used to detect and replace
    // out of date stored messages. For example, if message is for requesting transactions, we only need the most recent
    // one, that has the most up to date transactionId from the requester. Any other requests can be discarded.
    fun matches(other: StoredMessage) : Boolean {
        return from == other.from && to.publicKey == other.to.publicKey && messageStorageKey == other.messageStorageKey
    }
}