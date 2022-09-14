package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.*

class StandardSocketSession(private val socket: Socket) : SocketSession {
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    override suspend fun isReady() : Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    override suspend fun readSizedByteArray() : ByteArray {
        return ByteArray(readChannel.readInt()).also { readChannel.readFully(it, 0, it.size) }
    }

    override suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    override suspend fun close() {
        socket.close()
    }
}