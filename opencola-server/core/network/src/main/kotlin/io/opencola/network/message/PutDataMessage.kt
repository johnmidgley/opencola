package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.serialization.protobuf.Message as ProtoMessage
import io.opencola.serialization.protobuf.ProtoSerializable

class PutDataMessage(val id: Id, val data: ByteArray) : Message(messageType) {
    companion object : ProtoSerializable<PutDataMessage, ProtoMessage.PutDataMessage>  {
        const val messageType = "PutData"

        override fun toProto(value: PutDataMessage): ProtoMessage.PutDataMessage {
            return ProtoMessage.PutDataMessage
                .newBuilder()
                .setId(value.id.toProto())
                .setData(ByteString.copyFrom(value.data))
                .build()
        }

        override fun fromProto(value: ProtoMessage.PutDataMessage): PutDataMessage {
            return PutDataMessage(Id.fromProto(value.id), value.data.toByteArray())
        }

        fun encodeProto(value: PutDataMessage): ByteArray {
            return toProto(value).toByteArray()
        }

        fun decodeProto(value: ByteArray): PutDataMessage {
            return fromProto(ProtoMessage.PutDataMessage.parseFrom(value))
        }
    }

    override fun toProto(): ProtoMessage.PutDataMessage {
        return toProto(this)
    }
}