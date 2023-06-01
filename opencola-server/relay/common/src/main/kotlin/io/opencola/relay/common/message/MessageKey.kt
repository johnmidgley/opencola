package io.opencola.relay.common.message

import io.opencola.model.Id
import io.opencola.util.Base58
import io.opencola.util.toByteArray
import java.util.*

class MessageKey private constructor(val value: ByteArray?) {
    override fun toString(): String {
        return value?.let { Base58.encode(it) } ?: "none"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageKey) return false
        if (value == null && other.value == null) return true
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    companion object {
        private const val KEY_LENGTH_IN_BYTES = 8

        private fun bytesToKeyBytes(bytes: ByteArray): ByteArray {
            return Id.ofData(bytes).encoded().copyOfRange(0, KEY_LENGTH_IN_BYTES)
        }

        val none = MessageKey(null)

        fun unique(): MessageKey {
            return MessageKey(bytesToKeyBytes(UUID.randomUUID().toByteArray()))
        }

        fun of(bytes: ByteArray): MessageKey {
            return MessageKey(bytesToKeyBytes(bytes))
        }

        fun of(vararg ids: Id): MessageKey {
            return MessageKey(bytesToKeyBytes(ids.map { it.encoded() }.reduce { acc, bytes -> acc + bytes }))
        }

        fun of(key: String) : MessageKey {
            return MessageKey(bytesToKeyBytes(key.toByteArray()))
        }

        fun ofEncoded(key: ByteArray) : MessageKey {
            require(key.size == KEY_LENGTH_IN_BYTES) { "Key must be $KEY_LENGTH_IN_BYTES bytes" }
            return MessageKey(key)
        }
    }
}