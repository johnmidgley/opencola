package io.opencola.network.message

import io.opencola.relay.common.message.MessageKey

class PingMessage : UnsignedMessage(messageType, MessageKey.none, ByteArray(0)) {
    companion object {
        const val messageType = "Ping"
    }
}