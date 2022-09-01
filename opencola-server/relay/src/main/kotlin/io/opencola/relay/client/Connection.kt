package io.opencola.relay.client

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.IOException
import java.security.KeyPair

// From: https://subscription.packtpub.com/book/programming/9781801815727/10/ch10lvl1sec78/deferred-value
//suspend fun valueAsync(): Deferred<String> = coroutineScope {
//    val deferred = CompletableDeferred<String>()
//    launch {
//        delay(100)
//        if (Random.nextBoolean()) {
//            deferred.complete("OK")
//        }
//        else {
//            deferred.completeExceptionally(
//                RuntimeException()
//            )
//        }
//    }
//    deferred
//}

class Connection(private val socket: Socket) : Closeable {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)

    suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    suspend fun readSizedByteArray() : ByteArray {
        return ByteArray(readChannel.readInt()).also { readChannel.readFully(it, 0, it.size) }
    }

    suspend fun readInt() : Int {
        return readChannel.readInt()
    }

    suspend fun writeInt(i: Int) {
        writeChannel.writeInt(i)
    }

    override fun close() {
        socket.close()
    }
}