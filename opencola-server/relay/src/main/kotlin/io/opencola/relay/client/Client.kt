package io.opencola.relay.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.opencola.core.model.Id
import io.opencola.core.security.encrypt
import io.opencola.core.security.initProvider
import io.opencola.core.security.sign
import io.opencola.relay.common.Connection
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
            if (_connection == null || !_connection!!.isReady()) {
                logger.info { "Creating Connection for: ${Id.ofPublicKey(keyPair.public)}" }
                _connection = Connection(aSocket(selectorManager).tcp().connect(hostname, port = port)).also {
                    authenticate(it)
                }
            }

            _connection!!
        }
    }

    // This needs to be called before messages can be received from peers. Requests automatically open connections too.
    suspend fun connect() {
        getConnection()
    }

    // TODO: Should be private
    suspend fun sendControlMessage(code: Int, data: ByteArray): ByteArray? {
        return getConnection().transact("Sending control message") {
            // Empty ByteArray (empty receiver) means control message
            it.writeSizedByteArray(emptyByteArray)
            it.writeInt(code)
            it.writeSizedByteArray(data)
            it.readSizedByteArray()
        }
    }

    suspend fun sendMessage(publicKey: PublicKey, bytes: ByteArray): ByteArray? {
        val encryptedBytes = encrypt(publicKey, bytes)

        return getConnection().transact("Sending peer message") {
            // TODO: Have server generate session key, so public key can be encrypted in transport too
            it.writeSizedByteArray(publicKey.encoded)
            it.writeSizedByteArray(encryptedBytes)
            it.readSizedByteArray()
        }
    }

    override fun close() {
        _connection?.close()
        _connection = null
    }

    companion object {
        init{
            initProvider()
        }

        private val selectorManager = ActorSelectorManager(Dispatchers.IO)
        private val emptyByteArray = ByteArray(0)
    }
}