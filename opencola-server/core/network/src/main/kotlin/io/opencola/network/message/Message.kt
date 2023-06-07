package io.opencola.network.message

import com.google.protobuf.GeneratedMessageV3
import io.opencola.relay.common.message.MessageKey

// TODO: Make consistent with how Events are modeled
abstract class Message(val type: MessageType, val key: MessageKey) {
    abstract fun toProto(): GeneratedMessageV3

    open fun toUnsignedMessage() : UnsignedMessage {
        return if (this is UnsignedMessage) this else UnsignedMessage(type, key, toProto())
    }
}