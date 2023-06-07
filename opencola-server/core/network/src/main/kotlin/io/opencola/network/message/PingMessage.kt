package io.opencola.network.message

import io.opencola.relay.common.message.MessageKey

class PingMessage : UnsignedMessage(MessageType.PING, MessageKey.none, ByteArray(0))