package io.opencola.relay

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.core.security.sign
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import opencola.core.extensions.toByteArray
import java.io.Closeable
import java.util.*


// TODO: Multiplex connection?
class RelayConnection(private val socket: Socket) : Closeable {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)
    private val connectionMutex = Mutex()

    private suspend fun authenticate() {
        try {
            connectionMutex.withLock {
                val encodedPublicKey = readChannel.readSizedByteArray()
                val publicKey = publicKeyFromBytes(encodedPublicKey)

                // Send challenge
                // TODO: Use secure random bit generation
                val challenge = UUID.randomUUID().toByteArray()
                writeChannel.writeSizedByteArray(challenge)

                // Read signed challenge
                val challengeSignature = readChannel.readSizedByteArray()

                val status = if (isValidSignature(publicKey, challenge, challengeSignature)) 0 else -1
                writeChannel.writeInt(status)
                if (status != 0)
                    throw RuntimeException("Client failed to authenticate: ${socket.remoteAddress}")
            }
        } catch (e: Exception) {
            // TODO: Fix anti-pattern of catch all. Coroutines can be canceled, and this stops propagation.
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