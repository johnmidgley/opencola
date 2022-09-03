package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.RelayServer
import kotlinx.coroutines.*
import opencola.core.extensions.append
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

fun cancelJobs(vararg jobs: Job){
    jobs.forEach { it.cancel() }
}

class ConnectionTest {
    private val defaultPort = 5796

    @Test
    fun testSendResponse() {
        val keyPair0 = generateKeyPair()
        val keyPair1 = generateKeyPair()

        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.open() }
            relayServer.waitUntilOpen()

            val client0 = Client("0.0.0.0", defaultPort, keyPair0)
            val client0Job = launch { client0.open { "client0".toByteArray() } }
            client0.waitUntilOpen()

            val client1 = Client("0.0.0.0", defaultPort, keyPair1)
            val client1Job = launch { client1.open { payload -> payload.append(" client1".toByteArray()) } }
            client1.waitUntilOpen()

            val peerResponse = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            cancelJobs(client0Job, client1Job, serverJob)
        }
    }

    @Test
    fun testClientConnectBeforeServer(){
        runBlocking {
            val keyPair0 = generateKeyPair()
            val client0 = Client("0.0.0.0", defaultPort, keyPair0)
            val client0Job = launch { client0.open { "client0".toByteArray() } }

            // Give the client a chance to have a failed connection attempt
            delay(100)

            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.open() }
            relayServer.waitUntilOpen()

            val keyPair1 = generateKeyPair()
            val client1 = Client("0.0.0.0", defaultPort, keyPair1)
            val client1Job = launch { client1.open { payload -> payload.append(" client1".toByteArray()) } }
            client1.waitUntilOpen()

            val peerResponse = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            cancelJobs(client0Job, client1Job, serverJob)
        }
    }

    @Test
    fun testServerPartition() {

    }

    @Test
    fun testClientPartition() {

    }

    @Test
    fun testRandomClientsCalls() {

    }

    @Test
    fun testMultipleClientListen() {

    }
}