package io.opencola.relay

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import java.io.Closeable


// TODO: Multiplex connection?
class RelayConnection(private val socket: Socket) : Closeable {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)

    suspend fun start() {
        try {
            while (true) {
                val line = readChannel.readUTF8Line()
                writeChannel.writeStringUtf8("$line back\n")
            }
        } catch (e: Throwable) {
            println("Exception: $e")
        }
    }

    override fun close() {
        socket.close()
    }
}