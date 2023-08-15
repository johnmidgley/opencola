package io.opencola.network.message

import io.opencola.relay.common.message.v2.MessageStorageKey

class PingMessage : UnsignedMessage(MessageType.PING, MessageStorageKey.none, ByteArray(0))