package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.*
import java.io.Closeable

class ConnectedSocket(val socket: Socket) : Closeable {
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    fun isReady() : Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    suspend fun readSizedByteArray() : ByteArray {
        return ByteArray(readChannel.readInt()).also { readChannel.readFully(it, 0, it.size) }
    }

    suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    override fun close() {
        socket.close()
    }
}