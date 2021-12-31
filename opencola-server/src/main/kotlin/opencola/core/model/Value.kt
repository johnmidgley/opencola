package opencola.core.model

import kotlinx.serialization.Serializable
import opencola.core.serialization.ByteArrayStreamCodec
import java.io.InputStream
import java.io.OutputStream

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

    companion object Factory : ByteArrayStreamCodec<Value> {
        val emptyValue = Value("".toByteArray())

        override fun encode(stream: OutputStream, value: Value) {
            writeByteArray(stream, value.bytes)
        }

        override fun decode(stream: InputStream): Value {
            val bytes = readByteArray(stream)
            return if(bytes.isEmpty()) emptyValue else Value(bytes)
        }
    }
}