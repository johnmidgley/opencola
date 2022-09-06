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
    private var state = State.Initialized
    private var listenJob: Job? = null

    fun isReady(): Boolean {
        return !(state == State.Closed || socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    private fun isReadyOrThrow() {
        if(!isReady()){
            throw IllegalStateException("Connection is not ready")
        }
    }

    internal suspend fun readSizedByteArray() : ByteArray {
        isReadyOrThrow()
        return ByteArray(readChannel.readInt()).also { readChannel.readFully(it, 0, it.size) }
    }

    internal suspend fun writeSizedByteArray(byteArray: ByteArray) {
        isReadyOrThrow()
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
    }

    internal suspend fun readInt() : Int {
        isReadyOrThrow()
        return readChannel.readInt()
    }

    internal suspend fun writeInt(i: Int) {
        isReadyOrThrow()
        writeChannel.writeInt(i)
    }

    override fun close() {
        state = State.Closed
        socket.close()
        listenJob?.cancel()
        listenJob = null
    }

    suspend fun listen(handleMessage: suspend (ByteArray) -> Unit) = coroutineScope {
        if(state != State.Initialized)
            throw IllegalStateException("Connection is already listening")

        state = State.Opening

        listenJob = launch {
            state = State.Open

            while (isReady()) {
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