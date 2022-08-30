package io.opencola.relay.server.plugins

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.util.*
import io.ktor.application.*

fun Application.configureSockets() {


}

/**
 * Two mains are provided, you must first start EchoApp.Server, and then EchoApp.Client.
 * You can also start EchoApp.Server and then use a telnet client to connect to the echo server.
 */
object EchoApp {
    val selectorManager = ActorSelectorManager(Dispatchers.IO)
    val DefaultPort = 9002

    object Client {
        @JvmStatic
        fun main(args: Array<String>) {
            runBlocking {
                val socket = aSocket(selectorManager).tcp().connect("127.0.0.1", port = DefaultPort)
                val read = socket.openReadChannel()
                val write = socket.openWriteChannel(autoFlush = true)

                launch(Dispatchers.IO) {
                    while (true) {
                        val line = read.readUTF8Line()
                        println("server: $line")
                    }
                }

                for (line in System.`in`.lines()) {
                    println("client: $line")
                    write.writeStringUtf8("$line\n")
                }
            }
        }

        private fun InputStream.lines() = Scanner(this).lines()

        private fun Scanner.lines() = sequence {
            while (hasNext()) {
                yield(readLine())
            }
        }
    }
}

