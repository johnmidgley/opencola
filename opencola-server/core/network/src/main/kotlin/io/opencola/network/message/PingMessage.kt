package io.opencola.network.message

class PingMessage : UnsignedMessage(messageType, ByteArray(0)) {
    companion object {
        val messageType = "PingMessage"
    }
}