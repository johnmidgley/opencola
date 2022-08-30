package io.opencola.relay.client

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.opencola.core.security.sign
import io.opencola.relay.readSizedByteArray
import io.opencola.relay.writeSizedByteArray
import java.io.Closeable
import java.io.IOException
import java.security.KeyPair

class Connection(private val socket: Socket) : Closeable {
    private var authenticated = false
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)

    suspend fun authenticate(keyPair: KeyPair) {
        // Send public key
        writeChannel.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        val challengeBytes = readChannel.readSizedByteArray()

        // Sign challenge and send back
        writeChannel.writeSizedByteArray(sign(keyPair.private, challengeBytes))

        val authenticationResponse = readChannel.readInt()
        if(authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }

        authenticated = true
    }

    suspend fun writeLine(value: String) {
        if(!authenticated || socket.isClosed || writeChannel.isClosedForWrite){
            throw IOException("Client is not in a writable state")
        }

        writeChannel.writeStringUtf8("$value\n")
    }

    suspend fun readLine(): String? {
        if(!authenticated || socket.isClosed || readChannel.isClosedForRead){
            throw IOException("Client is not in a readable state")
        }
        return readChannel.readUTF8Line()
    }

    override fun close() {
        socket.close()
    }
}