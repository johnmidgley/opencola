package io.opencola.relay.common.connection

import io.opencola.model.Id
import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging
import java.security.PublicKey

typealias MessageHandler = suspend (ByteArray) -> Unit

class Connection(
    val publicKey: PublicKey,
    private val socketSession: SocketSession,
    val onClose: (Connection) -> Unit
) {
    val id = Id.ofPublicKey(publicKey)
    private val logger = KotlinLogging.logger("Connection[$id]")
    var state = Initialized
    private var listenJob: Job? = null

    suspend fun isReady(): Boolean {
        return state != Closed && socketSession.isReady()
    }

    private suspend fun isReadyOrThrow() {
        if (!isReady()) {
            close()
            throw IllegalStateException("Connection is not ready")
        }
    }

    private suspend fun readSizedByteArray(): ByteArray {
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
        onClose(this)

        logger.debug { "Closed" }
    }

    suspend fun listen(messageHandler: MessageHandler) = coroutineScope {
        if (state != Initialized)
            throw IllegalStateException("Connection is already listening")

        state = Opening

        listenJob = launch {
            state = Open

            while (isReady()) {
                try {
                    messageHandler(readSizedByteArray())
                } catch (e: CancellationException) {
                    logger.debug { "Cancelled" }
                    break
                } catch (e: ClosedReceiveChannelException) {
                    logger.debug { "Socket Closed" }
                    break
                } catch (e: Exception) {
                    logger.error { "$e" }
                }
            }

            close()
        }
    }
}