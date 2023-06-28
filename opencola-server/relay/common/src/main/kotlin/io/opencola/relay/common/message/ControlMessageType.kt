package io.opencola.relay.common.message

import io.opencola.relay.common.protobuf.Relay as Proto

enum class ControlMessageType(val protoType: Proto.ControlMessage.Type) {
    QUEUE_EMPTY(Proto.ControlMessage.Type.QUEUE_EMPTY);

    companion object {
        private val protoToTypeMap = ControlMessageType.values().associateBy { it.protoType }

        fun fromProto(protoType: Proto.ControlMessage.Type): ControlMessageType {
            return protoToTypeMap[protoType] ?: throw IllegalArgumentException("Unknown ControlMessageType: $protoType")
        }
    }
}