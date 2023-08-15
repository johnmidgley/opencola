package io.opencola.relay.common.message.v2

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.relay.common.protobuf.Relay as Proto

private val emptyByteArray = ByteArray(0)

class ControlMessage(val type: ControlMessageType, val payload: ByteArray = emptyByteArray) {
    companion object : ProtoSerializable<ControlMessage, Proto.Control> {
        override fun toProto(value: ControlMessage): Proto.Control {
            return Proto.Control.newBuilder()
                .setType(value.type.protoType)
                .also { if (value.payload.isNotEmpty()) it.setPayload(ByteString.copyFrom(value.payload)) }
                .build()
        }

        override fun fromProto(value: Proto.Control): ControlMessage {
            return ControlMessage(
                ControlMessageType.fromProto(value.type),
                if (value.hasPayload()) value.payload.toByteArray() else emptyByteArray
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Control {
            return Proto.Control.parseFrom(bytes)
        }
    }

    fun toProto(): Proto.Control {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return toProto().toByteArray()
    }
}