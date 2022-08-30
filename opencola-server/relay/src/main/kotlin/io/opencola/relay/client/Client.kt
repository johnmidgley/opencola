package io.opencola.relay.client

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import java.io.Closeable
import java.io.IOException
import java.security.KeyPair
import java.security.PublicKey

class Client(private val hostname: String, private val port: Int, private val keyPair: KeyPair) : Closeable {
    private val selectorManager = ActorSelectorManager(Dispatchers.IO)
    private var connection: Connection? = null

    // TODO: Move hostname:port here? Would allow multiple connections
    suspend fun connect() {
        connection = Connection(aSocket(selectorManager).tcp().connect(hostname, port = port))
        connection!!.authenticate(keyPair)
    }

    suspend fun writeLine(value: String){
        connection?.writeLine(value) ?: throw IllegalStateException("Client not connected")
    }

    suspend fun readLine(): String {
        return connection?.readLine() ?: throw IllegalStateException("Client not connected")
    }

    override fun close() {
        connection?.close()
        selectorManager.close()
    }
}