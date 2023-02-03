package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking

class StandardSocketSession(private val socket: Socket) : SocketSession {
    val maxReadSize = 1024 * 1024 * 50
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    override suspend fun isReady(): Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    override suspend fun readSizedByteArray(): ByteArray {
        val numBytes = readChannel.readInt()

        if (numBytes > maxReadSize) {
            throw IllegalArgumentException("Read size to big: $numBytes")
        }

        return ByteArray(numBytes).also { readChannel.readFully(it, 0, it.size) }
    }

    override suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
        writeChannel.flush()
    }

    override suspend fun close() {
        runBlocking { socket.close() }
    }
}