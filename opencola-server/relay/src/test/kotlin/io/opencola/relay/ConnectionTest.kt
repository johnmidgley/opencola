package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.RelayServer
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConnectionTest {
    private val defaultPort = 5796

    @Test
    fun testConnection() {
        val keyPair0 = generateKeyPair()
        val keyPair1 = generateKeyPair()

        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.run() }
            while(!relayServer.isStarted()){ delay(50) }

            val client0 = Client("0.0.0.0", defaultPort, keyPair0).also { it.connect() }
            val client1 = Client("0.0.0.0", defaultPort, keyPair1).also { it.connect() }

            val clientJob = launch {
                launch { client0.listen() }
                launch { client1.listen() }
            }

            val peerResponse = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello", String(peerResponse))

            clientJob.cancel()
            serverJob.cancel()
        }
    }
}