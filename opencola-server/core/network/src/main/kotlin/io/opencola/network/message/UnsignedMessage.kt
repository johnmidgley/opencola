package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.Message
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Message as ProtoMessage

open class UnsignedMessage(val type: String, val payload: ByteArray) {
    override fun toString(): String {
        return "UnsignedMessage(type='$type', payload=${payload.size} bytes)"
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

    companion object : ProtoSerializable<UnsignedMessage, ProtoMessage.UnsignedMessage> {
        override fun toProto(value: UnsignedMessage): Message.UnsignedMessage {
            return Message.UnsignedMessage.newBuilder()
                .setType(value.type)
                .setPayload(ByteString.copyFrom(value.payload))
                .build()
        }

        override fun fromProto(value: Message.UnsignedMessage): UnsignedMessage {
            return UnsignedMessage(value.type, value.payload.toByteArray())
        }
    }
}