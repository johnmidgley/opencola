package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.serialization.protobuf.Message as ProtoMessage
import io.opencola.serialization.protobuf.ProtoSerializable

class PutDataMessage(val id: Id, val data: ByteArray) : Message(messageType) {
    companion object : ProtoSerializable<PutDataMessage, ProtoMessage.UnsignedMessage>  {
        const val messageType = "PutDataMessage"

        override fun toProto(value: PutDataMessage): ProtoMessage.UnsignedMessage {
            val bytes = ProtoMessage.PutDataMessage
                .newBuilder()
                .setId(value.id.toProto())
                .setData(ByteString.copyFrom(value.data))
                .build()
                .toByteArray()

            return ProtoMessage.UnsignedMessage.newBuilder()
                .setType("PutDataMessage")
                .setPayload(ByteString.copyFrom(bytes))
                .build()
        }

        override fun fromProto(value: ProtoMessage.UnsignedMessage): PutDataMessage {
            require(value.type == "PutDataMessage")
            val proto = ProtoMessage.PutDataMessage.parseFrom(value.payload)
            return PutDataMessage(Id.fromProto(proto.id), proto.data.toByteArray())
        }

        fun encodeProto(value: PutDataMessage): ByteArray {
            return toProto(value).toByteArray()
        }

        fun decodeProto(value: ByteArray): PutDataMessage {
            return fromProto(ProtoMessage.UnsignedMessage.parseFrom(value))
        }
    }

    override fun toProto(): ProtoMessage.UnsignedMessage {
        return toProto(this)
    }
}