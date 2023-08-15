package io.opencola.network.message

import io.opencola.relay.common.message.v2.MessageKey

class PongMessage : UnsignedMessage(MessageType.PONG, MessageKey.none, ByteArray(0))
