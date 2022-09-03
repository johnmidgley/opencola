package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.RelayServer
import kotlinx.coroutines.*
import opencola.core.extensions.append
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConnectionTest {
    private val defaultPort = 5796

    @Test
    fun testSendResponse() {
        val keyPair0 = generateKeyPair()
        val keyPair1 = generateKeyPair()

        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.open() }
            while(!relayServer.isStarted()){ delay(50) }

            val client0 = Client("0.0.0.0", defaultPort, keyPair0).also { it.open() }
            val client1 = Client("0.0.0.0", defaultPort, keyPair1).also { it.open() }

            val clientJob = launch {
                launch { client0.listen { "client0".toByteArray() } }
                launch { client1.listen { payload -> payload.append(" client1".toByteArray()) } }
            }

            val peerResponse = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            clientJob.cancel()
            serverJob.cancel()
        }
    }

    @Test
    fun testClientConnectBeforeServer(){
        runBlocking {
            val keyPair0 = generateKeyPair()
        }
    }

    @Test
    fun testServerPartition(){

    }

    @Test
    fun testClientPartition(){

    }

    @Test
    fun testMultipleClientListen(){

    }
}