package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.serialization.protobuf.Message as ProtoMessage
import io.opencola.serialization.protobuf.ProtoSerializable

class GetDataMessage(val id: Id) : Message(messageType) {
    companion object : ProtoSerializable<GetDataMessage, ProtoMessage.GetDataMessage>  {
        const val messageType = "GetData"

        override fun toProto(value: GetDataMessage): ProtoMessage.GetDataMessage {
            return ProtoMessage.GetDataMessage
                .newBuilder()
                .setId(value.id.toProto())
                .build()
        }

        override fun fromProto(value: ProtoMessage.GetDataMessage): GetDataMessage {
            return GetDataMessage(Id.fromProto(value.id))
        }

        fun encodeProto(value: GetDataMessage): ByteArray {
            return toProto(value).toByteArray()
        }

        fun decodeProto(value: ByteArray): GetDataMessage {
            return fromProto(ProtoMessage.GetDataMessage.parseFrom(value))
        }
    }

    override fun toProto(): ProtoMessage.GetDataMessage {
        return toProto(this)
    }
}