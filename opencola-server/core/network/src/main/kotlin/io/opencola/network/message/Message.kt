package io.opencola.network.message

import com.google.protobuf.GeneratedMessageV3

// TODO: Make consistent with how Events are modeled
abstract class Message(val type: String) {
    abstract fun toProto(): GeneratedMessageV3

    open fun toUnsignedMessage() : UnsignedMessage {
        return if (this is UnsignedMessage) this else UnsignedMessage(type, toProto())
    }
}