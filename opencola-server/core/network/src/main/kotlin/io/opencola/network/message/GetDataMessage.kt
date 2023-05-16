package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.serialization.protobuf.Message as ProtoMessage
import io.opencola.serialization.protobuf.ProtoSerializable

class GetDataMessage(val id: Id) : Message(messageType) {
    companion object : ProtoSerializable<GetDataMessage, ProtoMessage.UnsignedMessage>  {
        const val messageType = "GetDataMessage"

        override fun toProto(value: GetDataMessage): ProtoMessage.UnsignedMessage {
            val bytes = ProtoMessage.GetDataMessage
                .newBuilder()
                .setId(value.id.toProto())
                .build()
                .toByteArray()

            return ProtoMessage.UnsignedMessage.newBuilder()
                .setType("GetDataMessage")
                .setPayload(ByteString.copyFrom(bytes))
                .build()
        }

        override fun fromProto(value: ProtoMessage.UnsignedMessage): GetDataMessage {
            require(value.type == "GetDataMessage")
            val proto = ProtoMessage.GetDataMessage.parseFrom(value.payload)
            return GetDataMessage(Id.fromProto(proto.id))
        }

        fun encodeProto(value: GetDataMessage): ByteArray {
            return toProto(value).toByteArray()
        }

        fun decodeProto(value: ByteArray): GetDataMessage {
            return fromProto(ProtoMessage.UnsignedMessage.parseFrom(value))
        }
    }

    override fun toProto(): ProtoMessage.UnsignedMessage {
        return toProto(this)
    }
}