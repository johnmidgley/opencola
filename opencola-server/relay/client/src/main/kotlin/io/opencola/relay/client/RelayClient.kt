package io.opencola.relay.client

import io.opencola.relay.common.State
import java.security.PublicKey

typealias MessageHandler = suspend (from: PublicKey, message: ByteArray) -> Unit

interface RelayClient {
    val publicKey: PublicKey
    val state: State
    val name: String?
    suspend fun open(messageHandler: MessageHandler)
    suspend fun waitUntilOpen()
    suspend fun sendMessage(to: PublicKey, body: ByteArray)
    suspend fun close()
}