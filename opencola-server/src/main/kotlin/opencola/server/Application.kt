package opencola.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import opencola.server.plugins.configureContentNegotiation
import opencola.server.plugins.configureHTTP
import opencola.server.plugins.configureRouting

fun main() {
    embeddedServer(Netty, port = 5795, host = "0.0.0.0") {
        configureHTTP()
        configureContentNegotiation()
        configureRouting()
    }.start(wait = true)
}
