package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.serialization.protobuf.Message as ProtoMessage
import io.opencola.serialization.protobuf.ProtoSerializable

class GetDataMessage(val ids: Set<Id>) {
    fun toProto(): ProtoMessage.UnsignedMessage {
        return toProto(this)
    }

    fun toMessage() : UnsignedMessage {
        return UnsignedMessage("GetDataMessage", toProto().toByteArray())
    }

    companion object : ProtoSerializable<GetDataMessage, ProtoMessage.UnsignedMessage>  {
        override fun toProto(value: GetDataMessage): ProtoMessage.UnsignedMessage {
            val bytes = ProtoMessage.GetDataMessage
                .newBuilder()
                .addAllIds(value.ids.map { Id.toProto(it) })
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
            return GetDataMessage(proto.idsList.map { Id.fromProto(it) }.toSet())
        }
    }
}