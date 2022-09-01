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

class Connection(private val socket: Socket, private val keyPair: KeyPair) : Closeable {
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

    suspend fun writeLine(value: String) {
        if (socket.isClosed || writeChannel.isClosedForWrite) {
            throw IOException("Client is not in a writable state")
        }

        withTimeout(10000) {
            writeChannel.writeStringUtf8("$value\n")
        }
    }

    suspend fun readLine(): String? {
        if (socket.isClosed || readChannel.isClosedForRead) {
            throw IOException("Client is not in a readable state")
        }

        return withTimeout(10000) {
            readChannel.readUTF8Line()
        }

    }

    override fun close() {
        socket.close()
    }
}