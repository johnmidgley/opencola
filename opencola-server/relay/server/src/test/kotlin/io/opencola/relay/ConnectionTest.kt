package io.opencola.relay

import io.ktor.server.netty.*
import io.opencola.io.StdoutMonitor
import io.opencola.relay.client.AbstractClient
import io.opencola.security.generateKeyPair
import io.opencola.relay.client.RelayClient
import io.opencola.relay.client.v1.WebSocketClient
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.State
import io.opencola.relay.server.startWebServer
import io.opencola.util.append
import io.opencola.relay.server.v1.WebSocketRelayServer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.net.ConnectException
import java.net.URI
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.math.abs
import kotlin.test.*

private val relayServerUri = URI("ocr://0.0.0.0")

class ConnectionTest {
    private class RelayServer(
        val webSocketRelayServer: WebSocketRelayServer,
        val nettyApplicationEngine: NettyApplicationEngine
    ) {
        suspend fun stop() {
            webSocketRelayServer.close()
            nettyApplicationEngine.stop(1000, 1000)
        }
    }

    private fun startServer(): RelayServer {
        return WebSocketRelayServer().let { RelayServer(it, startWebServer(defaultOCRPort, it)) }
    }

    private fun getClient(
        name: String,
        keyPair: KeyPair = generateKeyPair(),
        requestTimeoutInMilliseconds: Long = 5000,
    ): AbstractClient {
        return WebSocketClient(relayServerUri, keyPair, name, requestTimeoutInMilliseconds)
    }

    private suspend fun open(
        client: RelayClient,
        messageHandler: suspend (PublicKey, ByteArray) -> Unit = { _, _ -> println("Unhandled request") }
    ) = coroutineScope {
        launch { client.open(messageHandler) }
    }

    @Test
    fun testSendResponse() {
        runBlocking {
            val server = startServer()
            val result = CompletableDeferred<ByteArray>()
            val client0 = getClient(name = "client0").also {
                launch {
                    it.open { _, message ->
                        result.complete(message)
                        "".toByteArray()
                    }
                }
                it.waitUntilOpen()
            }
            val client1 = getClient(name = "client1")
                .also {
                    launch {
                        it.open { from, message ->
                            assertEquals(client0.publicKey, from)
                            val response = message.append(" client1".toByteArray())
                            it.sendMessage(from, response)
                        }
                    }

                    it.waitUntilOpen()
                }

            client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertEquals("hello client1", String(result.await()))

            server.stop()
            listOf(client0, client1).forEach { it.close() }
        }
    }

    @Test
    fun testClientConnectBeforeServer() {
        runBlocking {
            val result = CompletableDeferred<ByteArray>()
            val client0 = getClient("client0").also { launch { open(it) } }

            // Give the client a chance to have a failed connection attempt
            delay(100)
            assert(client0.state == State.Opening)

            val relayServer = startWebServer(defaultOCRPort)

            val client1 = getClient("client1")
                .also {
                    launch {
                        it.open { _, message ->
                            result.complete(message.append(" client1".toByteArray()))
                        }
                    }
                    it.waitUntilOpen()
                }

            client0.sendMessage(client1.publicKey, "hello".toByteArray())
            assertEquals("hello client1", String(result.await()))

            client0.close()
            client1.close()
            relayServer.stop(200, 200)
        }
    }

    @Test
    fun testServerPartition() {
        runBlocking {
            val relayServer0: RelayServer
            var relayServer1: RelayServer? = null
            var client0: RelayClient? = null
            var client1: RelayClient? = null
            val results = Channel<ByteArray>()

            try {
                relayServer0 = startServer()
                client0 = getClient("client0").also { launch { open(it) }; it.waitUntilOpen() }
                client1 = getClient("client1")
                    .also {
                        launch {
                            it.open { _, message ->
                                results.send(message.append(" client1".toByteArray()))
                            }
                        }
                        it.waitUntilOpen()
                    }

                client0.sendMessage(client1.publicKey, "hello1".toByteArray())
                assertEquals("hello1 client1", String(results.receive()))

                println("Stopping relay server")
                relayServer0.stop()

                println("Sending message to client1")
                assertFailsWith<ConnectException> { client0.sendMessage(client1.publicKey, "hello".toByteArray()) }

                println("Starting relay server again")
                StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                    relayServer1 = startServer()
                    // The waitUntil method does not yield to co-routines, so we need to explicitly delay
                    // to let the server start and the clients reconnect. The proper solution would be to
                    // implement a delayUntil method on StdoutMonitor that works properly with coroutines.
                    delay(2000)
                    // Wait until both clients have connected again
                    it.waitUntil("Connection created")
                    it.waitUntil("Connection created")
                }

                println("Sending message to client1")
                client0.sendMessage(client1.publicKey, "hello2".toByteArray())
                withTimeout(3000) {
                    assertEquals("hello2 client1", String(results.receive()))
                }
            } finally {
                println("Closing resources")
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
            val results = Channel<ByteArray>()
            val client0 = getClient(
                "client0",
                requestTimeoutInMilliseconds = 500
            ).also { launch { open(it) }; it.waitUntilOpen() }
            val client1KeyPair = generateKeyPair()
            val client1 = getClient("client1", client1KeyPair)
                .also {
                    launch {
                        it.open { _, message ->
                            results.send(message.append(" client1".toByteArray()))
                        }
                    }
                    it.waitUntilOpen()
                }

            println("Sending message")
            client0.sendMessage(client1.publicKey, "hello1".toByteArray())
            assertEquals("hello1 client1", String(results.receive()))

            println("Partitioning client")
            client1.close()

            println("Verifying partition")
            StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                client0.sendMessage(client1.publicKey, "hello".toByteArray())
                it.waitUntil("no connection to receiver")
            }

            println("Rejoining client")
            val client1Rejoin = getClient("client1", client1KeyPair)
                .also {
                    launch {
                        it.open { _, message ->
                            results.send(message.append(" client1".toByteArray()))
                        }
                    }
                    it.waitUntilOpen()
                }

            println("Verifying rejoin")
            client0.sendMessage(client1.publicKey, "hello2".toByteArray())
            assertEquals("hello2 client1", String(results.receive()))

            client0.close()
            client1.close()
            client1Rejoin.close()
            relayServer0.stop(200, 200)
        }
    }

    @Test
    fun testRandomClientsCalls() {
        runBlocking {
            // NOTE: Will fail on timeouts with to many calls (> 5000). Need to refactor for concurrency for real
            // load testing
            val results = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
            val relayServer = startWebServer(defaultOCRPort)
            val random = Random()
            val numClients = 50
            val clients = (0 until numClients)
                .map { getClient(name = "client$it", requestTimeoutInMilliseconds = 10000) }
                .onEach {
                    launch {
                        it.open { _, message ->
                            UUID.fromString(String(message)).let { uuid ->
                                results[uuid]?.complete(Unit)
                            }
                        }
                    }
                    it.waitUntilOpen()
                }

            (0..1000).map {
                val sender = abs(random.nextInt()) % numClients
                val receiver = abs(random.nextInt()) % numClients

                launch {
                    if (sender != receiver) {
                        val sendClient = clients[sender]
                        val receiveClient = clients[receiver]

                        UUID.randomUUID().let {
                            results[it] = CompletableDeferred()
                            sendClient.sendMessage(receiveClient.publicKey, it.toString().toByteArray())
                        }
                    }
                }
            }.parallelStream()
                .collect(Collectors.toList())
                .forEach { it.join() }

            withTimeout(10000) { results.forEach { (_, value) -> value.await() } }
            clients.forEach { it.close() }
            relayServer.stop(200, 200)
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
            val client0 = getClient(
                "client0",
                requestTimeoutInMilliseconds = 30000
            ).also { launch { open(it) }; it.waitUntilOpen() }
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