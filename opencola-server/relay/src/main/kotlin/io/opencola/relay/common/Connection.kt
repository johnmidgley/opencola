package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.io.Closeable

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
    private val logger = KotlinLogging.logger("Connection")
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)
    private var listening = false

    internal val connectionMutex = Mutex()

    fun isReady(): Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    internal suspend inline fun <reified T> transact(name: String = "", block: (Connection) -> T): T? {
        try {
            connectionMutex.withLock {
                return block(this)
            }
        } catch (e: Exception) {
            logger.error { "Error in transaction $name: $e" }
            return null
        }
    }

    internal suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    internal suspend fun readSizedByteArray() : ByteArray {
        return ByteArray(readChannel.readInt()).also { readChannel.readFully(it, 0, it.size) }
    }

    internal suspend fun readInt() : Int {
        return readChannel.readInt()
    }

    internal suspend fun writeInt(i: Int) {
        writeChannel.writeInt(i)
    }

    override fun close() {
        socket.close()
    }

    // NOTE: This is the only place reads should occur, outside authentication
    suspend fun listen(handleMessage: (ByteArray) -> Unit) = coroutineScope {
        if(listening) {
            throw IllegalStateException("Connection is already listening")
        }

        listening = true

        logger.info { "Connection Listening" }

        while (isActive) {
            try {
                val message = readSizedByteArray()
                logger.info { "Got message" }
                handleMessage(message)
            } catch(e: CancellationException){
                return@coroutineScope
            } catch (e: Exception) {
                logger.error { "$e" }
            }
        }
    }
}