package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.RelayServer
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ConnectionTest {
    private val defaultPort = 5796

    @Test
    fun testConnection() {
        val keyPair = generateKeyPair()
        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.run() }
            while(!relayServer.isStarted()){ delay(50) }

            val client = Client("0.0.0.0", defaultPort, keyPair).also { it.connect() }
            val message = "hello"
            val controlResponse = String(client.sendControlMessage(1, message.toByteArray())!!)
            assertEquals(message, controlResponse)

            val peerResponse = client.sendMessage(keyPair.public, "hello".toByteArray())
            assertEquals(0, peerResponse!!.size)

            serverJob.cancel()
        }
    }
}