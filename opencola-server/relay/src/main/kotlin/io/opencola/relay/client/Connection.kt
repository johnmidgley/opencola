package io.opencola.relay.client

import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.opencola.core.security.sign
import io.opencola.core.serialization.writeByteArray
import io.opencola.relay.readSizedByteArray
import io.opencola.relay.writeSizedByteArray
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.security.KeyPair
import java.security.PublicKey

// From: https://subscription.packtpub.com/book/programming/9781801815727/10/ch10lvl1sec78/deferred-value
//suspend fun valueAsync(): Deferred<String> = coroutineScope {
//    val deferred = CompletableDeferred<String>()
//    launch {
//        delay(100)
//        if (Random.nextBoolean()) {
//            deferred.complete("OK")
//        }
//        else {
//            deferred.completeExceptionally(
//                RuntimeException()
//            )
//        }
//    }
//    deferred
//}

class Connection(private val socket: Socket, private val keyPair: KeyPair) : Closeable {
    private val readChannel = socket.openReadChannel()
    private val writeChannel = socket.openWriteChannel(autoFlush = true)
    // TODO: Make finer grained?
    private val connectionMutex = Mutex()
    private var authenticated = false

    suspend fun authenticate() {
        if(!authenticated) {
            connectionMutex.withLock {
                // Send public key
                writeChannel.writeSizedByteArray(keyPair.public.encoded)

                // Read challenge
                val challengeBytes = readChannel.readSizedByteArray()

                // Sign challenge and send back
                writeChannel.writeSizedByteArray(sign(keyPair.private, challengeBytes))

                val authenticationResponse = readChannel.readInt()
                if (authenticationResponse != 0) {
                    throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
                }

                authenticated = true
            }
        }
    }

    suspend fun send(publicKey: PublicKey, bytes: ByteArray) {
        // TODO: Use Capnproto
        ByteArrayOutputStream().use {
            it.writeByteArray(publicKey.encoded)
            it.writeByteArray(bytes)

            withTimeout(10000) {
                writeChannel.writeSizedByteArray(it.toByteArray())
            }
        }
    }

    suspend fun writeLine(value: String) {
        if (!authenticated || socket.isClosed || writeChannel.isClosedForWrite) {
            throw IOException("Client is not in a writable state")
        }

        connectionMutex.withLock {
            withTimeout(10000) {
                writeChannel.writeStringUtf8("$value\n")
            }
        }
    }

    suspend fun readLine(): String? {
        if(!authenticated || socket.isClosed || readChannel.isClosedForRead){
            throw IOException("Client is not in a readable state")
        }

        connectionMutex.withLock {
            return withTimeout(10000) {
                readChannel.readUTF8Line()
            }
        }
    }

    override fun close() {
        socket.close()
    }
}