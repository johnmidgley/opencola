package io.opencola.relay.server.v1

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.relay.common.connection.StandardSocketSession
import io.opencola.relay.common.State.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class StandardSocketRelayServer(
    port: Int,
    numChallengeBytes: Int = 32

) : Server(numChallengeBytes) {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)

    override suspend fun open() = coroutineScope {
        if (state != Initialized) {
            throw IllegalStateException("Server has already been opened")
        }

        state = Opening

        listenJob = launch {
            logger.info("Relay Server listening at ${serverSocket.localAddress}")
            state = Open
            openMutex.unlock()

            while (state != Closed) {
                try {
                    val socketSession = StandardSocketSession(serverSocket.accept())
                    launch { handleSession(socketSession) }
                } catch (e: Exception) {
                    if (state == Closed || e is CancellationException) {
                        close()
                        break
                    } else {
                        logger.error { "Error accepting connection: $e" }
                        throw e
                    }
                }
            }
        }
    }

    override suspend fun close() {
        super.close()
        serverSocket.close()
        selectorManager.close()
    }
}