package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.network.protobuf.Message as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class GetDataMessage(val id: Id) : Message(MessageType.GET_DATA, MessageStorageKey.of(id)) {
    companion object : ProtoSerializable<GetDataMessage, Proto.GetDataMessage>  {
        override fun toProto(value: GetDataMessage): Proto.GetDataMessage {
            return Proto.GetDataMessage
                .newBuilder()
                .setId(value.id.toProto())
                .build()
        }

        override fun fromProto(value: Proto.GetDataMessage): GetDataMessage {
            return GetDataMessage(Id.fromProto(value.id))
        }

        override fun parseProto(bytes: ByteArray): Proto.GetDataMessage {
            return Proto.GetDataMessage.parseFrom(bytes)
        }
    }

    override fun toProto(): Proto.GetDataMessage {
        return toProto(this)
    }
}