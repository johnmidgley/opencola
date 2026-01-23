/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.common.connection

import io.opencola.model.Id
import io.opencola.relay.common.State.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import mu.KotlinLogging
import java.security.PublicKey

typealias MessageHandler = suspend (ByteArray) -> Unit

/**
 * The Connection class is used by the client and server facilitates low level communication
 * at the raw byte array level.
 */
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
        if(state == Closed)
            return

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