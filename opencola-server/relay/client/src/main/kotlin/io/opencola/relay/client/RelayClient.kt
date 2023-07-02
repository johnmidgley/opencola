package io.opencola.relay.client

import io.opencola.relay.common.message.MessageKey
import java.security.PublicKey

typealias MessageHandler = suspend (from: PublicKey, message: ByteArray) -> Unit
typealias EventHandler = suspend (publicKey: PublicKey, event: RelayEvent) -> Unit

interface RelayClient {
    suspend fun open(messageHandler: MessageHandler)

    suspend fun setEventHandler(eventHandler: EventHandler)
    suspend fun sendMessage(to: PublicKey, key: MessageKey, body: ByteArray)
    suspend fun close()
}