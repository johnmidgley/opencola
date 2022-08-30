package io.opencola.relay

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.core.security.sign
import opencola.core.extensions.toByteArray
import java.io.Closeable
import java.util.*


// TODO: Multiplex connection?
class RelayConnection(private val socket: Socket) : Closeable {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)

    private suspend fun authenticate() {
        try {
            val encodedPublicKey = readChannel.readSizedByteArray()
            val publicKey = publicKeyFromBytes(encodedPublicKey)

            // Send challenge
            // TODO: Use secure random bit generation
            val challenge = UUID.randomUUID().toByteArray()
            writeChannel.writeSizedByteArray(challenge)

            // Read signed challenge
            val challengeSignature = readChannel.readSizedByteArray()

            val status = if(isValidSignature(publicKey, challenge, challengeSignature)) 0 else -1
            writeChannel.writeInt(status)
        } catch (e: Exception){
            // TODO: Log
            close()
        }
    }

    suspend fun start() {
        try {
            authenticate()

            while (true) {
                val line = readChannel.readUTF8Line()
                writeChannel.writeStringUtf8("$line back\n")
            }
        } catch (e: Throwable) {
            println("Exception: $e")
        }
    }

    override fun close() {
        socket.close()
    }
}