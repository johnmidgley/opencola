package io.opencola.relay.common.message.v1

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageKey
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

// This class can be removed after V2 migration and replace by envelopeV2
class Envelope(val to: PublicKey, val key: MessageKey, val message: ByteArray) {
    override fun toString(): String {
        return "Envelope(to=${Id.ofPublicKey(to)}, key=$key, message=${message.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Envelope) return false
        if (to != other.to) return false
        if (key == MessageKey.none && other.key != MessageKey.none) return false
        if (key != MessageKey.none && other.key == MessageKey.none) return false
        if (key != MessageKey.none && key != other.key) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + (key.hashCode())
        result = 31 * result + message.contentHashCode()
        return result
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    companion object : StreamSerializer<Envelope> {

        // V1 encoding does not include the key
        override fun encode(stream: OutputStream, value: Envelope) {
            stream.writeByteArray(value.to.encoded)
            stream.writeByteArray(value.message)
        }

        // V1 encoding does not include the key
        override fun decode(stream: InputStream): Envelope {
            return Envelope(
                publicKeyFromBytes(stream.readByteArray()),
                MessageKey.none,
                stream.readByteArray()
            )
        }
    }
}