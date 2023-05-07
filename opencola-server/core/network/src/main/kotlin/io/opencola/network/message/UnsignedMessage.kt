package io.opencola.network.message

import io.opencola.serialization.*
import java.io.InputStream
import java.io.OutputStream

class UnsignedMessage(val type: String, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnsignedMessage

        if (type != other.type) return false
        return payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object : StreamSerializer<UnsignedMessage> {
        override fun encode(stream: OutputStream, value: UnsignedMessage) {
            stream.writeString(value.type)
            stream.writeByteArray(value.payload)
        }

        override fun decode(stream: InputStream): UnsignedMessage {
            return UnsignedMessage(stream.readString(), stream.readByteArray())
        }
    }
}