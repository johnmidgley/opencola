package io.opencola.relay.client

import io.opencola.relay.common.Header
import io.opencola.relay.common.State
import java.security.PublicKey

interface Client {
    val publicKey: PublicKey
    val state: State
    val name: String?
    suspend fun open(messageHandler: suspend (ByteArray) -> ByteArray)
    suspend fun waitUntilOpen()
    suspend fun sendMessage(to: PublicKey, body: ByteArray): ByteArray?
    suspend fun respondToMessage(messageHeader: Header, body: ByteArray)
    suspend fun close()
}