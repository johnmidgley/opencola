package io.opencola.relay

import io.ktor.utils.io.*

suspend fun ByteWriteChannel.writeSizedByteArray(byteArray: ByteArray){
    this.writeInt(byteArray.size)
    this.writeFully(byteArray)
}

suspend fun ByteReadChannel.readSizedByteArray() : ByteArray {
    return ByteArray(readInt()).also { readFully(it, 0, it.size) }
}