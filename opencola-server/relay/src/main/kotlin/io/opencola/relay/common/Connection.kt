package io.opencola.relay.common

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import mu.KotlinLogging
import java.io.Closeable

class Connection(private val socket: Socket, name: String? = null) : Closeable {
    private val logger = KotlinLogging.logger("Connection${if(name != null) " ($name)" else ""}")
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)
    private var listening = false

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
        listening = false
    }

    suspend fun listen(handleMessage: suspend (ByteArray) -> Unit) = coroutineScope {
        if(listening)
            throw IllegalStateException("Connection is already listening")
        else
            listening = true

        while (isActive && listening && isReady()) {
            try {
                handleMessage(readSizedByteArray())
            } catch(e: CancellationException) {
                break
            } catch(e: ClosedReceiveChannelException){
                break
            } catch (e: Exception) {
                logger.error { "$e" }
            }
        }
    }
}