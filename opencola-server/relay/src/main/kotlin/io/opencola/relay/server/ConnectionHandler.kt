package io.opencola.relay.server

import io.ktor.network.sockets.*
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.relay.common.Connection
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import java.io.Closeable
import java.security.PublicKey

class ConnectionHandler(private val connection: Connection,
                        private val handlerPeerMessage: suspend (PublicKey, ByteArray) -> ByteArray) : Closeable {
    private val logger = KotlinLogging.logger("RelayConnection")

    private fun handleControlMessage(code: Int, data: ByteArray): ByteArray {
        return when(code){
            // Echo data back
            1 ->  {
                logger.info { "Handling Echo" }
                data
            }
            else -> "ERROR".toByteArray()
        }
    }

    suspend fun start() {
        while (true) {
            try {
                val recipient = connection.readSizedByteArray()

                val result = if (recipient.isEmpty())
                    handleControlMessage(connection.readInt(), connection.readSizedByteArray())
                else
                    handlerPeerMessage(publicKeyFromBytes(recipient), connection.readSizedByteArray())

                connection.writeSizedByteArray(result)
            }
            catch (e: CancellationException) {
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