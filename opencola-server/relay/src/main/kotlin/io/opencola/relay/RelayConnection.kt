package io.opencola.relay

import io.ktor.network.sockets.*
import io.opencola.core.security.isValidSignature
import io.opencola.core.security.publicKeyFromBytes
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import opencola.core.extensions.toByteArray
import java.io.Closeable
import java.util.*


// TODO: Multiplex connection?
class RelayConnection(private val socket: Socket) : Closeable {
    private val logger = KotlinLogging.logger("RelayConnection")
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

    private suspend fun handleControlMessage() {
        when(readChannel.readInt()){
            // Echo data back
            1 ->  {
                logger.info { "Handling Echo" }
                writeChannel.writeSizedByteArray(readChannel.readSizedByteArray())
            }
            else -> writeChannel.writeSizedByteArray("ERROR".toByteArray())
        }
    }

    suspend fun start() {
        try {
            authenticate()

            while (true) {
                // val line = readChannel.readUTF8Line()
                // writeChannel.writeStringUtf8("$line back\n")
                val recipient = readChannel.readSizedByteArray()

                if(recipient.isEmpty()) {
                    handleControlMessage()
                }
            }
        } catch (e: Throwable) {
            println("Exception: $e")
        }
    }

    override fun close() {
        socket.close()
    }
}