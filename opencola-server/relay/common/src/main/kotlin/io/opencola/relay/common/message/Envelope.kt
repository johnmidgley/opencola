package io.opencola.relay.common.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.relay.common.protobuf.Relay
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import io.opencola.util.Base58
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

class Envelope(val to: PublicKey, val key: ByteArray?, val message: ByteArray) {
    override fun toString(): String {
        return "Envelope(to=${Id.ofPublicKey(to)}, key=${key?.let { Base58.encode(key) } ?: "null"}, message=${message.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Envelope) return false
        if (to != other.to) return false
        if (key == null && other.key != null) return false
        if (key != null && other.key == null) return false
        if (key != null && !key.contentEquals(other.key)) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + (key?.contentHashCode() ?: 0)
        result = 31 * result + message.contentHashCode()
        return result
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : StreamSerializer<Envelope>,
        ProtoSerializable<Envelope, Proto.Envelope> {

        // V1 encoding does not include the key
        override fun encode(stream: OutputStream, value: Envelope) {
            stream.writeByteArray(value.to.encoded)
            stream.writeByteArray(value.message)
        }

        // V1 encoding does not include the key
        override fun decode(stream: InputStream): Envelope {
            return Envelope(
                publicKeyFromBytes(stream.readByteArray()),
                null,
                stream.readByteArray()
            )
        }

        override fun toProto(value: Envelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setTo(ByteString.copyFrom(value.to.encoded))
                .also {if (value.key != null) it.setKey(ByteString.copyFrom(value.key)) }
                .setMessage(ByteString.copyFrom(value.message))
                .build()
        }

        override fun fromProto(value: Proto.Envelope): Envelope {
            return Envelope(
                publicKeyFromBytes(value.to.toByteArray()),
                if (value.key.isEmpty) null else value.key.toByteArray(),
                value.message.toByteArray()
            )
        }

        override fun parseProto(bytes: ByteArray): Relay.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }
    }
}