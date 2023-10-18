package io.opencola.relay.common.message.v2

import io.opencola.model.Id
import io.opencola.util.Base58
import io.opencola.util.toByteArray
import java.util.*

// TODO: Should value be nullable? The MessageStorageKey itself seems to be nullable, so it doesn't seem necessary here.
class MessageStorageKey private constructor(val value: ByteArray?) {
    override fun toString(): String {
        return value?.let { Base58.encode(it) } ?: "none"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageStorageKey) return false
        if (value == null && other.value == null) return true
        if (value != null && other.value == null) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    fun encoded() : ByteArray? {
        return value
    }

    companion object {
        private const val KEY_LENGTH_IN_BYTES = 8

        private fun bytesToKeyBytes(bytes: ByteArray): ByteArray {
            return Id.ofData(bytes).encoded().copyOfRange(0, KEY_LENGTH_IN_BYTES)
        }

        val none = MessageStorageKey(null)

        fun unique(): MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(UUID.randomUUID().toByteArray()))
        }

        fun of(bytes: ByteArray): MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(bytes))
        }

        fun of(vararg ids: Id): MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(ids.map { it.encoded() }.reduce { acc, bytes -> acc + bytes }))
        }

        fun of(key: String) : MessageStorageKey {
            return MessageStorageKey(bytesToKeyBytes(key.toByteArray()))
        }

        fun ofEncoded(key: ByteArray) : MessageStorageKey {
            require(key.size == KEY_LENGTH_IN_BYTES) { "Key must be $KEY_LENGTH_IN_BYTES bytes" }
            return MessageStorageKey(key)
        }
    }
}