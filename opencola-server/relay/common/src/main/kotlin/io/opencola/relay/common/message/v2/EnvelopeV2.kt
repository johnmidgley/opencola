package io.opencola.relay.common.message.v2

import com.google.protobuf.ByteString
import io.opencola.relay.common.protobuf.Relay
import io.opencola.security.EncryptedBytes
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class EnvelopeV2(val to: EncryptedBytes, val key: MessageStorageKey, val message: ByteArray) {
    override fun toString(): String {
        return "Envelope(to=ENCRYPTED, key=$key, message=${message.size} bytes)"
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EnvelopeV2) return false
        if (to != other.to) return false
        if (key == MessageStorageKey.none && other.key != MessageStorageKey.none) return false
        if (key != MessageStorageKey.none && other.key == MessageStorageKey.none) return false
        if (key != MessageStorageKey.none && key != other.key) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = to.hashCode()
        result = 31 * result + (key.hashCode())
        result = 31 * result + message.contentHashCode()
        return result
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<EnvelopeV2, Proto.Envelope> {
        override fun toProto(value: EnvelopeV2): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setTo(value.to.toProto())
                .also {if (value.key.value != null) it.setKey(ByteString.copyFrom(value.key.value)) }
                .setMessage(ByteString.copyFrom(value.message))
                .build()
        }

        override fun fromProto(value: Proto.Envelope): EnvelopeV2 {
            return EnvelopeV2(
                EncryptedBytes.fromProto(value.to),
                if (value.key.isEmpty) MessageStorageKey.none else MessageStorageKey.ofEncoded(value.key.toByteArray()),
                value.message.toByteArray()
            )
        }

        override fun parseProto(bytes: ByteArray): Relay.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }
    }
}