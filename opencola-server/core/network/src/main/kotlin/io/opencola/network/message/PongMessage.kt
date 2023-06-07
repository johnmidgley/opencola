package io.opencola.network.message

import io.opencola.relay.common.message.MessageKey

class PongMessage : UnsignedMessage(MessageType.PONG, MessageKey.none, ByteArray(0))
