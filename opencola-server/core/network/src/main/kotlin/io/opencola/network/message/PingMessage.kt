package io.opencola.network.message

import io.opencola.network.protobuf.Network as Proto
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.serialization.protobuf.ProtoSerializable

class PingMessage : Message(MessageStorageKey.none) {
    companion object : ProtoSerializable<PingMessage, Proto.PingMessage> {
        private val proto: Proto.PingMessage = Proto.PingMessage.newBuilder().build()
        override fun toProto(value: PingMessage): Proto.PingMessage {
            return proto
        }

        override fun fromProto(value: Proto.PingMessage): PingMessage {
            return PingMessage()
        }

        override fun parseProto(bytes: ByteArray): Proto.PingMessage {
            return proto
        }
    }
}