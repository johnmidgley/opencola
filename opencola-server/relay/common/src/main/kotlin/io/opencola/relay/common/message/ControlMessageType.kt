package io.opencola.relay.common.message

import io.opencola.relay.common.protobuf.Relay as Proto

enum class ControlMessageType(val protoType: Proto.Control.Type) {
    NO_PENDING_MESSAGES(Proto.Control.Type.NO_PENDING_MESSAGES);

    companion object {
        private val protoToTypeMap = ControlMessageType.values().associateBy { it.protoType }

        fun fromProto(protoType: Proto.Control.Type): ControlMessageType {
            return protoToTypeMap[protoType] ?: throw IllegalArgumentException("Unknown ControlMessageType: $protoType")
        }
    }
}