package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.*
import java.io.Closeable

class ConnectedSocket(val socket: Socket) : Closeable {
    private val maxReadSize = 1024 * 1024 * 50 // TODO: Make configurable
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    fun isReady() : Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    suspend fun readSizedByteArray() : ByteArray {
        val size = readChannel.readInt()

        if(size > maxReadSize) {
            throw RuntimeException("Read size too big: $size")
        }

        return ByteArray(size).also { readChannel.readFully(it, 0, it.size) }
    }

    suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    override fun close() {
        socket.close()
    }
}