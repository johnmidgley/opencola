package io.opencola.relay

import io.ktor.server.netty.*
import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.RelayClient
import io.opencola.relay.client.WebSocketClient
import io.opencola.relay.client.defaultOCRPort
import io.opencola.relay.common.State
import io.opencola.relay.server.startWebServer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.opencola.core.extensions.append
import io.opencola.relay.server.WebSocketRelayServer
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.test.*

private val relayServerUri = URI("ocr://0.0.0.0")

class ConnectionTest {
    private class RelayServer(val webSocketRelayServer: WebSocketRelayServer,
                              val nettyApplicationEngine: NettyApplicationEngine) {
        suspend fun stop() {
            webSocketRelayServer.close()
            nettyApplicationEngine.stop(1000,1000)
        }
    }

    private fun startServer() : RelayServer {
        return WebSocketRelayServer().let { RelayServer(it, startWebServer(defaultOCRPort, it)) }
    }

    private fun getClient(
        name: String,
        keyPair: KeyPair = generateKeyPair(),
        requestTimeoutInMilliseconds: Long = 5000,
    ) : RelayClient {
        return WebSocketClient(relayServerUri, keyPair, name, requestTimeoutInMilliseconds)
    }

    private suspend fun open(client: RelayClient,
                             messageHandler: suspend (PublicKey, ByteArray) -> ByteArray = { _, _ -> client.name!!.toByteArray() }
        ) = coroutineScope {
        launch { client.open(messageHandler) }
    }

    @Test
    fun testSendResponse() {
        runBlocking {
            val server = startServer()
            val client0 = getClient(name = "client0").also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient(name = "client1")
                .also {
                    launch {
                        it.open { k, p ->
                            assertEquals(client0.publicKey, k)
                            p.append(" client1".toByteArray()) }
                    }

                    it.waitUntilOpen()
                }

            val peerResponse = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            server.stop()
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

            val relayServer = startWebServer(defaultOCRPort)

            val client1 = getClient("client1")
                .also { launch { it.open { _, p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            client0.sendMessage(client1.publicKey, "hello".toByteArray()).also {
                assertNotNull(it)
                assertEquals("hello client1", String(it))
            }

            client0.close()
            client1.close()
            relayServer.stop(200,200)
        }
    }

    @Test
    fun testServerPartition() {
        runBlocking {
            val relayServer0: RelayServer
            var relayServer1: RelayServer? = null
            var client0: RelayClient? = null
            var client1: RelayClient? = null

            try {
                relayServer0 = startServer()
                client0 = getClient("client0").also { launch { open(it) }; it.waitUntilOpen() }
                client1 = getClient("client1")
                    .also { launch { it.open { _, p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

                val peerResponse0 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
                assertNotNull(peerResponse0)
                assertEquals("hello client1", String(peerResponse0))

                relayServer0.stop()

                val peerResponse1 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
                assertNull(peerResponse1)

                relayServer1 = startServer()

                val peerResponse2 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
                assertNotNull(peerResponse2)
                assertEquals("hello client1", String(peerResponse2))
            } finally {
                client0?.close()
                client1?.close()
                relayServer1?.stop()
            }
        }
    }


    @Test
    fun testClientPartition() {
        runBlocking {
            println("Starting server and clients")
            val relayServer0 = startWebServer(defaultOCRPort)
            val client0 = getClient("client0", requestTimeoutInMilliseconds = 500).also { launch { open(it) }; it.waitUntilOpen() }
            val client1KeyPair = generateKeyPair()
            val client1 = getClient("client1", client1KeyPair)
                .also { launch { it.open { _, p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

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
                .also { launch { it.open { _, p -> p.append(" client1".toByteArray()) } }; it.waitUntilOpen() }

            println("Verifying rejoin")
            val peerResponse2 = client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            client0.close()
            client1.close()
            client1Rejoin.close()
            relayServer0.stop(200,200)
        }
    }

    @Test
    fun testRandomClientsCalls() {
        runBlocking {
            // NOTE: Will fail on timeouts with to many calls (> 5000). Need to refactor for concurrency for real
            // load testing
            val relayServer = startWebServer(defaultOCRPort)

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
            relayServer.stop(200,200)
        }
    }

    @Test
    fun testMultipleClientListen() {
        runBlocking {
            var client: RelayClient? = null

            assertFails {
                runBlocking {
                    client = getClient("client0").also { launch { open(it) } }
                    open(client!!)
                }
            }

            client?.close()
        }
    }

    // @Test
    fun testLatency() {
        runBlocking {
            val relayServer = startWebServer(defaultOCRPort)
            val client0 = getClient("client0", requestTimeoutInMilliseconds = 30000).also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient("client1").also { launch { open(it) }; it.waitUntilOpen() }
            val data = ByteArray(1024 * 1024)

            try {
                (0..1).forEach { _ ->
                    val startTime = System.currentTimeMillis()
                    val response = client0.sendMessage(client1.publicKey, data)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    println("Elapsed time: ${elapsedTime / 1000}")
                    assertNotNull(response)
                }
            } finally {
                client0.close()
                client1.close()
                relayServer.stop()
            }
        }
    }
}