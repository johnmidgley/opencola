package io.opencola.relay.client

import java.security.PublicKey

typealias MessageHandler = suspend (from: PublicKey, message: ByteArray) -> Unit

interface RelayClient {
    suspend fun open(messageHandler: MessageHandler)
    suspend fun waitUntilOpen()
    suspend fun sendMessage(to: PublicKey, body: ByteArray)
    suspend fun close()
}