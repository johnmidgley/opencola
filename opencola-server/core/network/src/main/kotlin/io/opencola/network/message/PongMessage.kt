package io.opencola.network.message

class PongMessage : UnsignedMessage(messageType, ByteArray(0)) {
    companion object {
        val messageType = "PongMessage"
    }
}
