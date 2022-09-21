package io.opencola.core.model

import kotlinx.serialization.Serializable
import io.opencola.core.serialization.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

@Serializable
// TODO: Can codec be put here?
data class Value(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Value

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    companion object Factory : StreamSerializer<Value> {
        val emptyValue = Value("".toByteArray())

        override fun encode(stream: OutputStream, value: Value) {
            stream.writeByteArray(value.bytes)
        }

        override fun decode(stream: InputStream): Value {
            val bytes = stream.readByteArray()
            return if(bytes.isEmpty()) emptyValue else Value(bytes)
        }
    }
}

class MultiValueListItem(val key: UUID, val bytes: ByteArray) {
    fun toValue(): Value {
        ByteArrayOutputStream().use {
            it.writeUUID(key)
            it.writeByteArray(bytes)
            return Value(it.toByteArray())
        }
    }

    companion object Factory {
        fun keyOf(value: Value): UUID {
            ByteArrayInputStream(value.bytes).use { return it.readUUID() }
        }

        fun fromValue(value: Value): MultiValueListItem {
            ByteArrayInputStream(value.bytes).use {
                return MultiValueListItem(it.readUUID(), it.readByteArray())
            }
        }
    }
}

// TODO: Templatize
class MultiValueListOfStringItem(val key: UUID, val value: String?) {
    constructor(value: String?) : this (UUID.randomUUID(), value)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MultiValueListOfStringItem

        if (other.key != key) return false
        if (other.value != value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.hashCode()
        result = 31 * result + (value?.hashCode() ?: 0)
        return result
    }

    companion object Factory{
        fun fromMultiValue(multiValue: MultiValueListItem): MultiValueListOfStringItem {
            return MultiValueListOfStringItem(multiValue.key, String(multiValue.bytes))
        }
    }
}

