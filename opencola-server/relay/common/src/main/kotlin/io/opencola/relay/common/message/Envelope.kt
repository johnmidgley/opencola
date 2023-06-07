package io.opencola.relay.common.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.relay.common.protobuf.Relay
import io.opencola.security.PublicKeyProtoCodec
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

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
                MessageKey.none,
                stream.readByteArray()
            )
        }

        override fun toProto(value: Envelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setTo(PublicKeyProtoCodec.toProto(value.to))
                .also {if (value.key.value != null) it.setKey(ByteString.copyFrom(value.key.value)) }
                .setMessage(ByteString.copyFrom(value.message))
                .build()
        }

        override fun fromProto(value: Proto.Envelope): Envelope {
            return Envelope(
                PublicKeyProtoCodec.fromProto(value.to),
                if (value.key.isEmpty) MessageKey.none else MessageKey.ofEncoded(value.key.toByteArray()),
                value.message.toByteArray()
            )
        }

        override fun parseProto(bytes: ByteArray): Relay.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }
    }
}