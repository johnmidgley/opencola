package io.opencola.network.message

import io.opencola.serialization.protobuf.Message as ProtoMessage
import io.opencola.serialization.protobuf.ProtoSerializable

class PingMessage {
    fun toProto(): ProtoMessage.UnsignedMessage {
        return toProto(this)
    }

    fun toMessage() : UnsignedMessage {
        TODO("Clean this up - message isn't modelled correctly")
        return UnsignedMessage("PingMessage", ByteArray(0))
    }

    companion object : ProtoSerializable<PingMessage, ProtoMessage.UnsignedMessage> {
        private val pingMessage = PingMessage()
        private val unsignedMessage = toProto(pingMessage)

        override fun toProto(value: PingMessage): ProtoMessage.UnsignedMessage {
            return unsignedMessage
        }

        override fun fromProto(value: ProtoMessage.UnsignedMessage): PingMessage {
            require(value.type == "PingMessage")
            return pingMessage
        }
    }
}
