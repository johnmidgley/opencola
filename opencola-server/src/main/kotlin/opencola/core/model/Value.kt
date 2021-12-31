package opencola.core.model

import kotlinx.serialization.Serializable
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

    companion object Factory : ByteArrayStreamCodec<ByteArray> {
        override fun encode(stream: OutputStream, value: ByteArray): OutputStream {
            TODO("Not yet implemented")
        }

        override fun decode(stream: InputStream): ByteArray {
            TODO("Not yet implemented")
        }

    }
}