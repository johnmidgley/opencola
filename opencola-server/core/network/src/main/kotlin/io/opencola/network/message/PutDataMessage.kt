package io.opencola.network.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.network.protobuf.Message as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class PutDataMessage(val id: Id, val data: ByteArray) : Message(MessageType.PUT_DATA, MessageStorageKey.of(id)) {
    companion object : ProtoSerializable<PutDataMessage, Proto.PutDataMessage>  {
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