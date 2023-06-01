package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.relay.common.message.MessageKey
import io.opencola.serialization.protobuf.Message as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class PutDataMessage(val id: Id, val data: ByteArray) : Message(messageType, MessageKey.of(id)) {
    companion object : ProtoSerializable<PutDataMessage, Proto.PutDataMessage>  {
        const val messageType = "PutData"

        override fun toProto(value: PutDataMessage): Proto.PutDataMessage {
            return Proto.PutDataMessage
                .newBuilder()
                .setId(value.id.toProto())
                .setData(ByteString.copyFrom(value.data))
                .build()
        }

        override fun fromProto(value: Proto.PutDataMessage): PutDataMessage {
            return PutDataMessage(Id.fromProto(value.id), value.data.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.PutDataMessage {
            return Proto.PutDataMessage.parseFrom(bytes)
        }
    }

    override fun toProto(): Proto.PutDataMessage {
        return toProto(this)
    }
}