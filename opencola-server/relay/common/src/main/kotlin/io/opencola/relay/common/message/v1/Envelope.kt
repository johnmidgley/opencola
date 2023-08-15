package io.opencola.relay.common.message.v1

import io.opencola.model.Id
import io.opencola.relay.common.message.AbstractEnvelope
import io.opencola.relay.common.message.Recipient
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

// This class can be removed after V2 migration and replace by envelopeV2
// TODO: MessageStorageKey is not used in V1, remove it
class Envelope(val to: PublicKey, messageStorageKey: MessageStorageKey, message: ByteArray) :
    AbstractEnvelope(listOf(Recipient(to)), messageStorageKey, message) {
    override fun toString(): String {
        return "Envelope(to=${Id.ofPublicKey(to)}, key=$messageStorageKey, message=${message.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Envelope) return false
        if (to != other.to) return false
        if (messageStorageKey == MessageStorageKey.none && other.messageStorageKey != MessageStorageKey.none) return false
        if (messageStorageKey != MessageStorageKey.none && other.messageStorageKey == MessageStorageKey.none) return false
        if (messageStorageKey != MessageStorageKey.none && messageStorageKey != other.messageStorageKey) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + (messageStorageKey.hashCode())
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
                MessageStorageKey.none,
                stream.readByteArray()
            )
        }
    }
}