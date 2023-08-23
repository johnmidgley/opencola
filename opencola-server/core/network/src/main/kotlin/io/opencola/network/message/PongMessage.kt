package io.opencola.network.message

import io.opencola.network.protobuf.Network as Proto
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.serialization.protobuf.ProtoSerializable

class PongMessage : Message(MessageStorageKey.none) {
    companion object : ProtoSerializable<PongMessage, Proto.PongMessage> {
        private val proto: Proto.PongMessage = Proto.PongMessage.newBuilder().build()

        override fun toProto(value: PongMessage): Proto.PongMessage {
            return proto
        }

        override fun fromProto(value: Proto.PongMessage): PongMessage {
            return PongMessage()
        }

        override fun parseProto(bytes: ByteArray): Proto.PongMessage {
            return proto
        }
    }
}
