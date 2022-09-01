package io.opencola.relay.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.security.encrypt
import io.opencola.core.security.sign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging
import java.io.Closeable
import java.security.KeyPair
import java.security.PublicKey

class Client(private val hostname: String, private val port: Int, private val keyPair: KeyPair) : Closeable {
    private val logger = KotlinLogging.logger("Client")

    // Not to be touched directly. Access by calling getConnections, which will ensure it's opened and ready
    private var _connection: Connection? = null
    private val connectionMutex = Mutex()

    // Should only be called once, right after connection to server
    private suspend fun authenticate(connection: Connection) {
        // Send public key
        connection.writeSizedByteArray(keyPair.public.encoded)

        // Read challenge
        val challengeBytes = connection.readSizedByteArray()

        // Sign challenge and send back
        connection.writeSizedByteArray(sign(keyPair.private, challengeBytes))

        val authenticationResponse = connection.readInt()
        if (authenticationResponse != 0) {
            throw RuntimeException("Unable to authenticate connection: $authenticationResponse")
        }

        logger.info { "Connection authenticated" }
    }

    private suspend fun getConnection() : Connection {
        return connectionMutex.withLock {
            if (_connection == null) {
                logger.info { "Creating Connection" }
                _connection = Connection(aSocket(selectorManager).tcp().connect(hostname, port = port)).also {
                    authenticate(it)
                }
            }

            // TODO: Make sure connection is still alive before returning
            _connection!!
        }
    }

    // This needs to be called before messages can be received from peers. Requests automatically open connections too.
    suspend fun connect() {
        getConnection()
    }

    suspend fun send(publicKey: PublicKey, bytes: ByteArray) : ByteArray? {
        val encryptedBytes = encrypt(publicKey, bytes)
        return null
    }

    // TODO: Should be private
    suspend fun sendControlMessage(code: Int, data: ByteArray) : ByteArray {
        getConnection().let {
            // Empty ByteArray (empty receiver) means control message
            it.writeSizedByteArray(emptyByteArray)
            it.writeInt(code)
            it.writeSizedByteArray(data)
            return it.readSizedByteArray()
        }
    }

    override fun close() {
        _connection?.close()
        _connection = null
    }

    companion object {
        private val selectorManager = ActorSelectorManager(Dispatchers.IO)
        private val emptyByteArray = ByteArray(0)
    }
}