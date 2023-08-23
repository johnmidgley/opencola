package io.opencola.network.message

import io.opencola.model.Id
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.network.protobuf.Network as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class GetDataMessage(val dataId : Id) : Message(MessageStorageKey.of(dataId)) {
    companion object : ProtoSerializable<GetDataMessage, Proto.GetDataMessage>  {
        override fun toProto(value: GetDataMessage): Proto.GetDataMessage {
            return Proto.GetDataMessage
                .newBuilder()
                .setId(value.dataId.toProto())
                .build()
        }

        override fun fromProto(value: Proto.GetDataMessage): GetDataMessage {
            return GetDataMessage(Id.fromProto(value.id))
        }

        override fun parseProto(bytes: ByteArray): Proto.GetDataMessage {
            return Proto.GetDataMessage.parseFrom(bytes)
        }
    }
}