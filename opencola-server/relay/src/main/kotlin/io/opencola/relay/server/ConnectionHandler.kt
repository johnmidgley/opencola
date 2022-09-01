package io.opencola.relay.server

import io.opencola.core.security.publicKeyFromBytes
import io.opencola.relay.common.Connection
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import java.io.Closeable
import java.security.PublicKey

internal class ConnectionHandler(
    private val connection: Connection,
    private val handlePeerMessage: suspend (PublicKey, ByteArray) -> ByteArray
) : Closeable {
    private val logger = KotlinLogging.logger("RelayConnection")

    private fun handleControlMessage(code: Int, data: ByteArray): ByteArray {
        return when (code) {
            // Echo data back
            1 -> {
                logger.info { "Handling Echo" }
                data
            }
            else -> "ERROR".toByteArray()
        }
    }

    suspend fun deliverMessage(from: PublicKey, data: ByteArray) {
        // TODO: UNSAFE
        connection.writeSizedByteArray(from.encoded)
        connection.writeSizedByteArray(data)
    }

    suspend fun run() {
        while (true) {
            try {
                val recipient = connection.readSizedByteArray()

                connection.transact {
                    val result = if (recipient.isEmpty())
                        handleControlMessage(it.readInt(), it.readSizedByteArray())
                    else
                        handlePeerMessage(publicKeyFromBytes(recipient), it.readSizedByteArray())

                    it.writeSizedByteArray(result)
                }
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