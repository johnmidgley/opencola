package io.opencola.relay.server

import io.opencola.relay.common.Connection
import mu.KotlinLogging
import java.io.Closeable


// TODO: Multiplex connection?
class ConnectionHandler(private val connection: Connection) : Closeable {
    private val logger = KotlinLogging.logger("RelayConnection")

    private suspend fun handleControlMessage() {
        when(connection.readInt()){
            // Echo data back
            1 ->  {
                logger.info { "Handling Echo" }
                connection.writeSizedByteArray(connection.readSizedByteArray())
            }
            else -> connection.writeSizedByteArray("ERROR".toByteArray())
        }
    }

    suspend fun start() {
        try {
            while (true) {
                // val line = readChannel.readUTF8Line()
                // writeChannel.writeStringUtf8("$line back\n")
                val recipient = connection.readSizedByteArray()

                if(recipient.isEmpty()) {
                    handleControlMessage()
                }
            }
        } catch (e: Throwable) {
            println("Exception: $e")
        }
    }

    override fun close() {
        connection.close()
    }
}