package io.opencola.relay.server

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RelayServer(private val port: Int) {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
    private var started = false

    fun isStarted() : Boolean {
        return started
    }

    suspend fun run() = coroutineScope() {
        try {
            println("Relay Server listening at ${serverSocket.localAddress}")
            started = true
            while (isActive) {
                val socket = serverSocket.accept()
                println("Accepted ${socket.remoteAddress}")
                launch { ConnectionHandler(socket).use { it.start() } }
            }
        } finally {
            serverSocket.close()
            selectorManager.close()
        }
    }

}