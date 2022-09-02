package io.opencola.relay.server

import io.opencola.core.model.Id
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.relay.common.Connection
import io.opencola.relay.common.MessageEnvelope
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import java.io.Closeable
import java.security.PublicKey

internal class ConnectionHandler(
    private val connection: Connection,
    private val handlePeerMessage: suspend (PublicKey, ByteArray) -> ByteArray
) : Closeable {
    private val logger = KotlinLogging.logger("RelayConnection")

    // NOTE: This is the only place (outside of authentication) that any reads should occur.
    suspend fun run() {
        while (true) {
            try {
                val envelope = MessageEnvelope.decode(connection.readSizedByteArray())
                logger.info { "Received message to: ${Id.ofPublicKey(envelope.to)}" }
            } catch (e: CancellationException) {
                return
            } catch (e: Exception) {
                logger.error("${e.stackTrace}")
            }
        }
    }

    override fun close() {
        connection.close()
    }
}