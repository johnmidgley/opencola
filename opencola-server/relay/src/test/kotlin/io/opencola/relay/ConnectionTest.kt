package io.opencola.relay

import io.ktor.util.collections.*
import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.RelayServer
import kotlinx.coroutines.*
import opencola.core.extensions.append
import java.io.Closeable
import java.security.KeyPair
import java.util.*
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val defaultHost = "0.0.0.0"
private const val defaultPort = 5796

class ConnectionTest {
    class TestResources : Closeable {
        private val resources = ConcurrentList<Closeable>()

        fun add(resource: Closeable){
            resources.add(resource)
        }

        override fun close() {
            resources.forEach { it.close() }
        }
    }

    private fun getClient(name: String,
                          keyPair: KeyPair = generateKeyPair()): Client {
        return Client(defaultHost, defaultPort, keyPair, name = name)

    }

    private suspend fun open(client: Client,
                             messageHandler: suspend (ByteArray) -> ByteArray = { _ -> client.name!!.toByteArray() }
        ) = coroutineScope {
        launch { client.open(messageHandler) }
    }

    @Test
    fun testSendResponse() {
        runBlocking {
            val relayServer = RelayServer(defaultPort).also { launch { it.open(); it.waitUntilOpen() } }
            val client0 = getClient("client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient("client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            val peerResponse = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            listOf(client0, client1, relayServer).forEach { it.close() }
        }
    }

    @Test
    fun testClientConnectBeforeServer() {
        runBlocking {
            val client0 = getClient("client0").also { launch { open(it) } }

            // Give the client a chance to have a failed connection attempt
            delay(100)

            val relayServer = RelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen()}
            val client1 = getClient("client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            client0.sendMessage(client1.publicKey, "hello".toByteArray()).also {
                assertNotNull(it)
                assertEquals("hello client1", String(it))
            }

            listOf(client0, client1, relayServer).forEach { it.close() }
        }
    }

    @Test
    fun testServerPartition() {
        runBlocking {
            val relayServer0 = RelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen() }
            val client0 = getClient("client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient("client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            val peerResponse0 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse0)
            assertEquals("hello client1", String(peerResponse0))

            relayServer0.close()

            val peerResponse1 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNull(peerResponse1)

            val relayServer1 = RelayServer(defaultPort).also { launch { it.open(); it.waitUntilOpen() } }

            val peerResponse2 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            listOf(client0, client1, relayServer1).forEach { it.close() }
        }
    }

    @Test
    fun testClientPartition() {
        runBlocking {
            println("Starting server and clients")
            val relayServer0 = RelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen() }
            val client0 = getClient("client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1KeyPair = generateKeyPair()
            val client1 = getClient("client1", client1KeyPair)
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            println("Sending message")
            val peerResponse0 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse0)
            assertEquals("hello client1", String(peerResponse0))

            println("Partitioning client")
            client1.close()

            println("Verifying partition")
            // TODO: Investigate the double connection error in logs at this point
            val peerResponse1 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNull(peerResponse1)

            println("Rejoining client")
            val client1Rejoin = getClient("client1", client1KeyPair)
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            println("Verifying rejoin")
            val peerResponse2 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            listOf(client0, client1Rejoin, relayServer0).forEach { it.close() }
        }
    }

    @Test
    fun testRandomClientsCalls() {
        runBlocking {
            // TODO: This does seem to fail after enough calls
            val relayServer = RelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen() }

            val numClients = 20
            val clients = (0 until numClients).map { getClient("client$it") }
            clients.forEach { launch { open(it) }; it.waitUntilOpen() }

            val random = Random()

            (0..1000).map {
                val sender = abs(random.nextInt()) % numClients
                val receiver = abs(random.nextInt()) % numClients

                launch {
                    if(sender != receiver) {
                        val sendClient = clients[sender]
                        val receiveClient = clients[receiver]

                        val response = sendClient.sendMessage(receiveClient.publicKey, "hello".toByteArray())
                        assertNotNull(response)
                        assertEquals("client$receiver", String(response))
                    }
                }
            }.forEach { it.join() }

            clients.forEach { it.close() }
            relayServer.close()
        }
    }

    @Test
    fun testMultipleClientListen() {

    }
}