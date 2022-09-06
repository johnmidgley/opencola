package io.opencola.relay.common

import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging
import java.io.Closeable

class Connection(private val connectedSocket: ConnectedSocket, val name: String? = null) : Closeable {
    private val logger = KotlinLogging.logger("Connection${if(name != null) " ($name)" else ""}")
    private var state = Initialized
    private var listenJob: Job? = null

    fun isReady(): Boolean {
        return state != Closed && connectedSocket.isReady()
    }

    private fun isReadyOrThrow() {
        if(!isReady()){
            throw IllegalStateException("Connection is not ready")
        }
    }

    private suspend fun readSizedByteArray() : ByteArray {
        isReadyOrThrow()
        return connectedSocket.readSizedByteArray()
    }

    internal suspend fun writeSizedByteArray(byteArray: ByteArray) {
        isReadyOrThrow()
        connectedSocket.writeSizedByteArray(byteArray)
    }

    override fun close() {
        state = Closed
        connectedSocket.close()
        listenJob?.cancel()
        listenJob = null
        logger.debug { "Closed" }
    }

    suspend fun listen(handleMessage: suspend (ByteArray) -> Unit) = coroutineScope {
        if(state != Initialized)
            throw IllegalStateException("Connection is already listening")

        state = Opening

        listenJob = launch {
            state = Open

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