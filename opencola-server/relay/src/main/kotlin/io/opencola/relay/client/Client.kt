package io.opencola.relay.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.security.encrypt
import kotlinx.coroutines.Dispatchers
import java.io.Closeable
import java.security.KeyPair
import java.security.PublicKey

class Client(private val connection: Connection, private val keyPair: KeyPair) : Closeable {

    suspend fun send(publicKey: PublicKey, bytes: ByteArray) : ByteArray? {
        val encryptedBytes = encrypt(publicKey, bytes)
        connection.send(publicKey, encryptedBytes)

        return null
    }

    suspend fun writeLine(value: String) {


        connection.writeLine(value)
    }

    suspend fun readLine(): String? {
        return connection.readLine()
    }

    override fun close() {
        connection.close()
    }

    companion object {
        private val selectorManager = ActorSelectorManager(Dispatchers.IO)

        suspend fun connect(hostname: String, port: Int, keyPair: KeyPair): Client {
            val connection = Connection(aSocket(selectorManager).tcp().connect(hostname, port = port), keyPair).also {
                it.authenticate()
            }

            return Client(connection, keyPair)
        }
    }
}