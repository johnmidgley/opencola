package io.opencola.relay.common

import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging

typealias MessageHandler = suspend (ByteArray) -> Unit

class Connection(private val socketSession: SocketSession, val name: String? = null) {
    private val logger = KotlinLogging.logger("Connection${if(name != null) " ($name)" else ""}")
    private var state = Initialized
    private var listenJob: Job? = null

    suspend fun isReady(): Boolean {
        return state != Closed && socketSession.isReady()
    }

    private suspend fun isReadyOrThrow() {
        if(!isReady()){
            throw IllegalStateException("Connection is not ready")
        }
    }

    private suspend fun readSizedByteArray() : ByteArray {
        isReadyOrThrow()
        return socketSession.readSizedByteArray()
    }

    suspend fun writeSizedByteArray(byteArray: ByteArray) {
        isReadyOrThrow()
        socketSession.writeSizedByteArray(byteArray)
    }

    suspend fun close() {
        state = Closed
        socketSession.close()
        listenJob?.cancel()
        listenJob = null
        logger.debug { "Closed" }
    }

    suspend fun listen(messageHandler: MessageHandler) = coroutineScope {
        if(state != Initialized)
            throw IllegalStateException("Connection is already listening")

        state = Opening

        listenJob = launch {
            state = Open

            while (isReady()) {
                try {
                    messageHandler(readSizedByteArray())
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