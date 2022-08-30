package io.opencola.relay

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import java.io.IOException

class Client(private val socket: Socket) {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)

    suspend fun writeLine(value: String) {
        if(socket.isClosed || writeChannel.isClosedForWrite){
            throw IOException("Client is not in a writable state")
        }

        writeChannel.writeStringUtf8("$value\n")
    }

    suspend fun readLine(): String? {
        if(socket.isClosed || readChannel.isClosedForRead){
            throw IOException("Client is not in a readable state")
        }
        return readChannel.readUTF8Line()
    }

    companion object Factory {
        private val selectorManager = ActorSelectorManager(Dispatchers.IO)

        suspend fun connect(hostname: String, port: Int): Client {
            return Client(aSocket(selectorManager).tcp().connect(hostname, port = port))
        }
    }
}