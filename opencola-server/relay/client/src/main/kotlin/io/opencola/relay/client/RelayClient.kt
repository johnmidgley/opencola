package io.opencola.relay.client

import io.opencola.relay.common.message.MessageKey
import java.security.PublicKey

typealias MessageHandler = suspend (from: PublicKey, message: ByteArray) -> Unit

interface RelayClient {
    suspend fun open(messageHandler: MessageHandler)

    // TODO: Remove this and add onOpened: (RelayClient) -> Unit handler to open()
    suspend fun waitUntilOpen()
    suspend fun sendMessage(to: PublicKey, key: MessageKey, body: ByteArray)
    suspend fun close()
}