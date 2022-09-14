package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.client.StandardSocketClient
import io.opencola.relay.client.WebSocketClient
import io.opencola.relay.common.State
import io.opencola.relay.server.StandardSocketRelayServer
import io.opencola.relay.server.startWebServer
import kotlinx.coroutines.*
import opencola.core.extensions.append
import java.security.KeyPair
import java.util.*
import java.util.stream.Collectors
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.test.*

private const val defaultHost = "0.0.0.0"
private const val defaultPort = 5796

class ConnectionTest {
    private fun getClient(
        name: String,
        keyPair: KeyPair = generateKeyPair(),
        requestTimeoutInMilliseconds: Long = 5000,
    ) : Client {
        return StandardSocketClient(defaultHost, defaultPort, keyPair, name, requestTimeoutInMilliseconds)
    }

    private fun getWebSocketClient(
        name: String,
        keyPair: KeyPair = generateKeyPair(),
        requestTimeoutInMilliseconds: Long = 5000,
    ): WebSocketClient {
        return WebSocketClient(defaultHost, defaultPort, keyPair, name, requestTimeoutInMilliseconds)
    }

    private suspend fun open(client: Client,
                             messageHandler: suspend (ByteArray) -> ByteArray = { _ -> client.name!!.toByteArray() }
        ) = coroutineScope {
        launch { client.open(messageHandler) }
    }

    @Test
    fun testSendResponse() {
        runBlocking {
            val relayServer = StandardSocketRelayServer(defaultPort).also { launch { it.open(); it.waitUntilOpen() } }
            val client0 = getClient(name = "client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient(name = "client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            val peerResponse = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            client0.close()
            client1.close()
            relayServer.close()
        }
    }

    @Test
    fun testSendResponseWebSocket() {
        runBlocking {
            val relayWebServer = startWebServer(defaultPort)
            val client0 = getWebSocketClient(name = "client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getWebSocketClient(name = "client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            val peerResponse = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            relayWebServer.stop(200, 200)

            listOf(client0, client1).forEach { it.close() }
        }
    }

    @Test
    fun testClientConnectBeforeServer() {
        runBlocking {
            val client0 = getClient("client0").also { launch { open(it) } }

            // Give the client a chance to have a failed connection attempt
            delay(100)
            assert(client0.state == State.Opening)

            val relayServer = StandardSocketRelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen()}
            val client1 = getClient("client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            client0.sendMessage(client1.publicKey, "hello".toByteArray()).also {
                assertNotNull(it)
                assertEquals("hello client1", String(it))
            }

            client0.close()
            client1.close()
            relayServer.close()
        }
    }

    @Test
    fun testServerPartition() {
        runBlocking {
            val relayServer0 = StandardSocketRelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen() }
            val client0 = getClient("client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient("client1")
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            val peerResponse0 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse0)
            assertEquals("hello client1", String(peerResponse0))

            relayServer0.close()

            val peerResponse1 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNull(peerResponse1)

            val relayServer1 = StandardSocketRelayServer(defaultPort).also { launch { it.open(); it.waitUntilOpen() } }

            val peerResponse2 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            client0.close()
            client1.close()
            relayServer0.close()
            relayServer1.close()
        }
    }

    @Test
    fun testClientPartition() {
        runBlocking {
            println("Starting server and clients")
            val relayServer0 = StandardSocketRelayServer(defaultPort).also { launch { it.open() }; it.waitUntilOpen() }
            val client0 = getClient("client0", requestTimeoutInMilliseconds = 500).also { launch { open(it) }; it.waitUntilOpen() }
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
            val peerResponse1 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNull(peerResponse1)

            println("Rejoining client")
            val client1Rejoin = getClient("client1", client1KeyPair)
                .also { launch { it.open { p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            println("Verifying rejoin")
            val peerResponse2 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            client0.close()
            client1.close()
            client1Rejoin.close()
            relayServer0.close()
        }
    }

    @Test
    fun testRandomClientsCalls() {
        runBlocking {
            // NOTE: Will fail on timeouts with to many calls (> 5000). Need to refactor for concurrency for real
            // load testing
            val relayServer = StandardSocketRelayServer(defaultPort)
            thread {
                runBlocking { launch { relayServer.open() } }
            }
            relayServer.waitUntilOpen()

            val numClients = 50
            val clients = (0 until numClients).map { getClient(name = "client$it", requestTimeoutInMilliseconds = 10000) }
            clients.forEach { launch { open(it) }; it.waitUntilOpen() }

            val random = Random()

            (0..1000).map {
                val sender = abs(random.nextInt()) % numClients
                val receiver = abs(random.nextInt()) % numClients

                launch {
                    if (sender != receiver) {
                        val sendClient = clients[sender]
                        val receiveClient = clients[receiver]

                        val response = sendClient.sendMessage(receiveClient.publicKey, "hello".toByteArray())
                        assertNotNull(response)
                        assertEquals("client$receiver", String(response))
                    }
                }
            }.parallelStream()
                .collect(Collectors.toList())
                .forEach { it.join() }

            clients.forEach { it.close() }
            relayServer.close()
        }
    }

    @Test
    fun testMultipleClientListen() {
        runBlocking {
            var client: Client? = null

            assertFails {
                runBlocking {
                    client = getClient("client0").also { launch { open(it) } }
                    open(client!!)
                }
            }

            client?.close()
        }
    }
}