package io.opencola.relay.common.message.v2.store

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.EncryptedBytes
import io.opencola.security.SignedBytes

data class StoredMessageHeader(
    val from: Id,
    val to: Id,
    val storageKey: MessageStorageKey,
    val secretKey: EncryptedBytes,
    val timeMilliseconds: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "Header(from=$from, to=$to, messageStorageKey=$storageKey, messageSecretKey=ENCRYPTED) time=$timeMilliseconds"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredMessageHeader) return false

        if (from != other.from) return false
        if (to != other.to) return false
        if (storageKey != other.storageKey) return false
        if (secretKey != other.secretKey) return false
        if (timeMilliseconds != other.timeMilliseconds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + storageKey.hashCode()
        result = 31 * result + secretKey.hashCode()
        result = 31 * result + timeMilliseconds.hashCode()
        return result
    }

    fun matches(other: StoredMessageHeader) : Boolean {
        return from == other.from && to == other.to && storageKey == other.storageKey
    }
}

data class StoredMessage (
    val header: StoredMessageHeader,
    val body: SignedBytes,
) {
    constructor(
        from: Id,
        to: Id,
        storageKey: MessageStorageKey,
        secretKey: EncryptedBytes,
        body: SignedBytes,
        timeMilliseconds: Long = System.currentTimeMillis()
    ) : this(StoredMessageHeader(from, to, storageKey, secretKey, timeMilliseconds), body)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredMessage) return false

        if (header != other.header) return false
        if (body != other.body) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + body.hashCode()
        return result
    }

    override fun toString(): String {
        return "StoredMessage(header=$header body=${body.bytes.size} bytes)"
    }

    // Stored messages match if they have the same from, to, and messageStorageKey. This is used to detect and replace
    // out of date stored messages. For example, if message is for requesting transactions, we only need the most recent
    // one, that has the most up to date transactionId from the requester. Any other requests can be discarded.
    fun matches(other: StoredMessage) : Boolean {
        return header.matches(other.header)
    }
}