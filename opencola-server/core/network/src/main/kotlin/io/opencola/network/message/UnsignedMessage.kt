package io.opencola.network.message

import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import io.opencola.relay.common.message.MessageKey
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.network.protobuf.Message as Proto

// "Unsigned" is more specific and does not conflict with Protobuf naming
open class UnsignedMessage(type: MessageType, key: MessageKey, val payload: ByteArray) : Message(type, key) {
    constructor(type: MessageType, key: MessageKey, protoMessage: GeneratedMessageV3) : this(
        type,
        key,
        protoMessage.toByteArray()
    )

    companion object : ProtoSerializable<UnsignedMessage, Proto.UnsignedMessage> {
        override fun toProto(value: UnsignedMessage): Proto.UnsignedMessage {
            return Proto.UnsignedMessage.newBuilder()
                .setType(MessageTypeProtoCodec.toProto(value.type))
                .setPayload(ByteString.copyFrom(value.payload))
                .build()
        }

        override fun fromProto(value: Proto.UnsignedMessage): UnsignedMessage {
            return UnsignedMessage(
                MessageTypeProtoCodec.fromProto(value.type),
                MessageKey.none,
                value.payload.toByteArray()
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.UnsignedMessage {
            return Proto.UnsignedMessage.parseFrom(bytes)
        }
    }

    override fun toString(): String {
        return "UnsignedMessage(type='$type', payload=${payload.size} bytes)"
    }

    override fun toProto(): GeneratedMessageV3 {
        return toProto(this)
    }

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
}