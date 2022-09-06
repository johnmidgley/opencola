package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging
import java.io.Closeable

class Connection(private val socket: Socket, name: String? = null) : Closeable {
    private val logger = KotlinLogging.logger("Connection${if(name != null) " ($name)" else ""}")
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)
    private var listenJob: Job? = null

    fun isReady(): Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    internal suspend fun readSizedByteArray() : ByteArray {
        return ByteArray(readChannel.readInt()).also { readChannel.readFully(it, 0, it.size) }
    }

    internal suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    internal suspend fun readInt() : Int {
        return readChannel.readInt()
    }

    internal suspend fun writeInt(i: Int) {
        writeChannel.writeInt(i)
    }

    override fun close() {
        socket.close()
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun listen(handleMessage: suspend (ByteArray) -> Unit) = coroutineScope {
        // TODO: Add close check here and in client and server
        if(listenJob != null)
            throw IllegalStateException("Connection is already listening")

        listenJob = launch {
            while (listenJob != null && isReady()) {
                try {
                    handleMessage(readSizedByteArray())
                } catch (e: CancellationException) {
                    logger.debug { "Cancelled" }
                    close()
                    break
                } catch (e: ClosedReceiveChannelException) {
                    logger.debug { "Socket Closed" }
                    close()
                    break
                } catch (e: Exception) {
                    logger.error { "$e" }
                }
            }
        }
    }
}