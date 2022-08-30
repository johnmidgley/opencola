package io.opencola.relay.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.relay.server.plugins.configureRouting
import io.opencola.relay.server.plugins.configureSockets
import io.opencola.relay.RelayConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun startWebServer(){
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSockets()
        configureRouting()
    }.start()
}

fun runRelayServer(port: Int) {
    runBlocking {
        val selectorManager = ActorSelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind(port = port)
        println("Relay Server listening at ${serverSocket.localAddress}")
        while (true) {
            val socket = serverSocket.accept()
            println("Accepted ${socket.remoteAddress}")
            launch {
                RelayConnection(socket).use {
                    it.start()
                }
            }
        }
    }
}

fun main() {
    startWebServer()
    runRelayServer(5796)
}