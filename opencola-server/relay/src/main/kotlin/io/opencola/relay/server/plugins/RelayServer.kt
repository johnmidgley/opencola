package io.opencola.relay.server.plugins

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.relay.RelayConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class RelayServer(private val port: Int, private val onStarted: () -> Unit = {}) {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)

    suspend fun run() = coroutineScope {
        try {
            println("Relay Server listening at ${serverSocket.localAddress}")
            onStarted()
            while (true) {
                val socket = serverSocket.accept()
                println("Accepted ${socket.remoteAddress}")
                launch {
                    RelayConnection(socket).use {
                        it.start()
                    }
                }
            }
        } finally {
            serverSocket.close()
            selectorManager.close()
        }
    }

}