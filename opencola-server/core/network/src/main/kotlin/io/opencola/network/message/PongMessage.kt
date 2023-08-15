package io.opencola.network.message

import io.opencola.relay.common.message.v2.MessageStorageKey

class PongMessage : UnsignedMessage(MessageType.PONG, MessageStorageKey.none, ByteArray(0))
