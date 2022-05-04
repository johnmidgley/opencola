package opencola.core.model

import kotlinx.serialization.Serializable
import opencola.core.extensions.append
import opencola.core.extensions.toByteArray
import opencola.core.serialization.*
import java.io.ByteArrayInputStream
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

class MultiValue(val key: UUID, val value: ByteArray) {
    fun toValue(): Value {
        return Value(key.toByteArray().append(value))
    }

    companion object Factory {
        fun fromValue(value: Value): MultiValue {
            ByteArrayInputStream(value.bytes).use {
                return MultiValue(it.readUUID(), it.readByteArray())
            }
        }
    }


}