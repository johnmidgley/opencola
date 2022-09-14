package io.opencola.relay.common

interface SocketSession {
    suspend fun isReady() : Boolean
    suspend fun readSizedByteArray() : ByteArray
    suspend fun writeSizedByteArray(byteArray: ByteArray)
    suspend fun close()
}