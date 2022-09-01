package io.opencola.relay.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.opencola.relay.server.plugins.configureRouting
import io.opencola.relay.server.plugins.configureSockets
import kotlinx.coroutines.runBlocking

fun startWebServer(){
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSockets()
        configureRouting()
    }.start()
}

fun main() {
    startWebServer()

    runBlocking {
        RelayServer(5796).run()
    }
}