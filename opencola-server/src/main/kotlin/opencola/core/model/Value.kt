package opencola.core.model

import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

@Serializable
// TODO: Change value to bytes
// TODO: Can codec be put here?
data class Value(val value: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Value

        if (!value.contentEquals(other.value)) return false

        return true
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }

    companion object Factory : ByteArrayStreamCodec<ByteArray> {
        override fun encode(stream: OutputStream, value: ByteArray): OutputStream {
            TODO("Not yet implemented")
        }

        override fun decode(stream: InputStream): ByteArray {
            TODO("Not yet implemented")
        }

    }
}