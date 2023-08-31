package io.opencola.relay

import io.opencola.io.StdoutMonitor
import io.opencola.relay.client.AbstractClient
import io.opencola.security.generateKeyPair
import io.opencola.relay.client.RelayClient
import io.opencola.relay.client.v1.WebSocketClient as WebSocketClientV1
import io.opencola.relay.client.v2.WebSocketClient as WebSocketClientV2
import io.opencola.relay.common.defaultOCRPort
import io.opencola.relay.common.State
import io.opencola.relay.common.message.v2.MessageStorageKey
import io.opencola.relay.server.RelayServer
import io.opencola.relay.server.startWebServer
import io.opencola.util.append
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

class ConnectionTest {
    private val localRelayServerUri = URI("ocr://0.0.0.0")
    private val prodRelayServerUri = URI("ocr://relay.opencola.net")

    private enum class ClientType {
        V1,
        V2
    }

    private fun getClient(
        clientType: ClientType,
        name: String,
        keyPair: KeyPair = generateKeyPair(),
        requestTimeoutInMilliseconds: Long = 5000,
        relayServerUri: URI = localRelayServerUri,
    ): AbstractClient {
        return when (clientType) {
            ClientType.V1 -> WebSocketClientV1(relayServerUri, keyPair, name, requestTimeoutInMilliseconds)
            ClientType.V2 -> WebSocketClientV2(relayServerUri, keyPair, name, requestTimeoutInMilliseconds)
        }
    }

    private suspend fun open(
        client: RelayClient,
        messageHandler: suspend (PublicKey, ByteArray) -> Unit = { _, _ -> println("Unhandled request") }
    ) = coroutineScope {
        launch { client.open(messageHandler) }
    }

    private fun testAuthentication(clientType: ClientType, relayServerUri: URI = localRelayServerUri) {
        println("testAuthentication($clientType, $relayServerUri)")
        runBlocking {
            var server: RelayServer? = null
            var client0: AbstractClient? = null

            try {
                StdoutMonitor(readTimeoutMilliseconds = 3000).use { monitor ->
                    println("Starting RelayServer")
                    server = if (relayServerUri == localRelayServerUri) RelayServer().also { it.start() } else null
                    println("Starting client0")
                    client0 = getClient(clientType, "client0", relayServerUri = relayServerUri).also {
                        launch { it.open { _, _ -> "".toByteArray() } }
                        it.waitUntilOpen()
                    }

                    monitor.waitUntil("Session authenticated for:")
                }
            } finally {
                println("Closing resources")
                server?.stop()
                client0?.close()
            }
        }
    }

    @Test
    fun testAuthenticationV1() {
        testAuthentication(ClientType.V1)
    }

    @Test
    fun testAuthenticationV2() {
        testAuthentication(ClientType.V2)
    }

    private fun testSendResponse(clientType: ClientType, relayServerUri: URI = localRelayServerUri) {
        runBlocking {
            var server: RelayServer? = null
            var client0: AbstractClient? = null
            var client1: AbstractClient? = null

            try {
                server = if (relayServerUri == localRelayServerUri) RelayServer().also { it.start() } else null
                val result = CompletableDeferred<ByteArray>()
                client0 = getClient(clientType, "client0", relayServerUri = relayServerUri).also {
                    launch {
                        it.open { _, message ->
                            result.complete(message)
                            "".toByteArray()
                        }
                    }
                    it.waitUntilOpen()
                }
                client1 = getClient(clientType, "client1", relayServerUri = relayServerUri)
                    .also {
                        launch {
                            it.open { from, message ->
                                assertEquals(client0.publicKey, from)
                                val response = message.append(" client1".toByteArray())
                                it.sendMessage(from, MessageStorageKey.unique(), response)
                            }
                        }

                        it.waitUntilOpen()
                    }

                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello".toByteArray())
                withTimeout(3000) { assertEquals("hello client1", String(result.await())) }
            } finally {
                println("Closing resources")
                server?.stop()
                listOf(client0, client1).forEach { it?.close() }
            }
        }
    }

    @Test
    fun testSendResponseV1() {
        testSendResponse(ClientType.V1)
    }

    @Test
    fun testSendResponseV2() {
        testSendResponse(ClientType.V2)
    }

    private fun testClientConnectBeforeServer(clientType: ClientType) {
        runBlocking {
            var server: RelayServer? = null
            var client0: AbstractClient? = null
            var client1: AbstractClient? = null

            try {
                val result = CompletableDeferred<ByteArray>()
                client0 = getClient(clientType, "client0").also { launch { open(it) } }

                // Give the client a chance to have a failed connection attempt
                delay(100)
                assert(client0.state == State.Opening)

                println("Starting relay server")
                server = RelayServer().also { it.start() }

                println("Waiting for client0 to open")
                client1 = getClient(clientType, "client1")
                    .also {
                        launch {
                            it.open { _, message ->
                                result.complete(message.append(" client1".toByteArray()))
                            }
                        }
                        it.waitUntilOpen()
                    }

                println("Sending message")
                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello".toByteArray())
                withTimeout(3000) { assertEquals("hello client1", String(result.await())) }
            } finally {
                client0?.close()
                client1?.close()
                server?.stop()
            }
        }
    }

    @Test
    fun testClientConnectBeforeServerV1() {
        testClientConnectBeforeServer(ClientType.V1)
    }

    @Test
    fun testClientConnectBeforeServerV2() {
        testClientConnectBeforeServer(ClientType.V2)
    }

    private fun testServerPartition(clientType: ClientType) {
        runBlocking {
            val server0: RelayServer
            var server1: RelayServer? = null
            var client0: RelayClient? = null
            var client1: RelayClient? = null
            val results = Channel<ByteArray>()

            try {
                server0 = RelayServer().also { it.start() }
                client0 = getClient(clientType, "client0").also { launch { open(it) }; it.waitUntilOpen() }
                client1 = getClient(clientType, "client1")
                    .also {
                        launch {
                            it.open { _, message ->
                                results.send(message.append(" client1".toByteArray()))
                            }
                        }
                        it.waitUntilOpen()
                    }

                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello1".toByteArray())
                assertEquals("hello1 client1", String(results.receive()))

                println("Stopping relay server")
                server0.stop()

                println("Sending message to client1")
                assertFailsWith<ConnectException> {
                    client0.sendMessage(
                        client1.publicKey,
                        MessageStorageKey.unique(),
                        "hello".toByteArray()
                    )
                }

                println("Starting relay server again")
                StdoutMonitor(readTimeoutMilliseconds = 3000).use { stdoutMonitor ->
                    server1 = RelayServer().also { it.start() }
                    // The waitUntil method does not yield to co-routines, so we need to explicitly delay
                    // to let the server start and the clients reconnect. The proper solution would be to
                    // implement a delayUntil method on StdoutMonitor that works properly with coroutines.
                    delay(2000)
                    // Wait until both clients have connected again
                    stdoutMonitor.waitUntil("Connection created")
                    stdoutMonitor.waitUntil("Connection created")
                }

                println("Sending message to client1")
                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello2".toByteArray())
                withTimeout(3000) {
                    assertEquals("hello2 client1", String(results.receive()))
                }
            } finally {
                println("Closing resources")
                client0?.close()
                client1?.close()
                server1?.stop()
            }
        }
    }

    @Test
    fun testServerPartitionV1() {
        testServerPartition(ClientType.V1)
    }

    @Test
    fun testServerPartitionV2() {
        testServerPartition(ClientType.V2)
    }

    private fun testClientPartition(clientType: ClientType, relayServerUri: URI = localRelayServerUri) {
        println("testClientPartition($clientType)")
        runBlocking {
            var server: RelayServer? = null
            var client0: AbstractClient? = null
            var client1: AbstractClient? = null
            var client1Rejoin: AbstractClient? = null

            try {
                println("Starting server and clients")
                server = if (relayServerUri == localRelayServerUri) RelayServer().also { it.start() } else null
                val results = Channel<ByteArray>()
                client0 = getClient(
                    clientType,
                    "client0",
                    requestTimeoutInMilliseconds = 500,
                    relayServerUri = relayServerUri
                ).also { launch { open(it) }; it.waitUntilOpen() }
                val client1KeyPair = generateKeyPair()
                client1 = getClient(clientType, "client1", client1KeyPair, relayServerUri = relayServerUri)
                    .also {
                        launch {
                            it.open { _, message ->
                                results.send(message.append(" client1".toByteArray()))
                            }
                        }
                        it.waitUntilOpen()
                    }

                println("Sending message")
                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello1".toByteArray())
                withTimeout(1000) { assertEquals("hello1 client1", String(results.receive())) }

                println("Partitioning client")
                StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                    client1.close()
                    it.waitUntil("Closed - client1")
                }

                if (relayServerUri == localRelayServerUri) {
                    println("Verifying partition")
                    StdoutMonitor(readTimeoutMilliseconds = 3000).use {
                        client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello".toByteArray())
                        it.waitUntil("no connection to receiver")
                    }
                }

                println("Rejoining client")
                client1Rejoin = getClient(clientType, "client1", client1KeyPair, relayServerUri = relayServerUri)
                    .also {
                        launch {
                            it.open { _, message ->
                                results.send(message.append(" client1".toByteArray()))
                            }
                        }
                        it.waitUntilOpen()
                    }

                println("Verifying rejoin (stored message + new one)")
                client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), "hello2".toByteArray())

                // V2 Supports storage, so check that first message is available
                if (clientType == ClientType.V2)
                    assertEquals("hello client1", String(results.receive()))

                assertEquals("hello2 client1", String(results.receive()))
            } finally {
                println("Closing resources")
                client0?.close()
                client1?.close()
                client1Rejoin?.close()
                server?.stop()
            }
        }
    }

    @Test
    fun testClientPartitionV1() {
        testClientPartition(ClientType.V1)
    }

    @Test
    fun testClientPartitionV2() {
        testClientPartition(ClientType.V2)
    }

    private fun testRandomClientsCalls(clientType: ClientType) {
        runBlocking {
            var server: RelayServer? = null
            var clients: List<AbstractClient>? = null

            try {
                // NOTE: Will fail on timeouts with to many calls (> 5000). Need to refactor for concurrency for real
                // load testing
                val results = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
                server = RelayServer().also { it.start() }
                val random = Random()
                val numClients = 50
                clients = (0 until numClients)
                    .map { getClient(clientType, name = "client$it", requestTimeoutInMilliseconds = 10000) }
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
                                sendClient.sendMessage(
                                    receiveClient.publicKey,
                                    MessageStorageKey.unique(),
                                    it.toString().toByteArray()
                                )
                            }
                        }
                    }
                }.parallelStream()
                    .collect(Collectors.toList())
                    .forEach { it.join() }

                withTimeout(10000) { results.forEach { (_, value) -> value.await() } }
            } finally {
                println("Closing resources")
                clients?.forEach { it.close() }
                server?.stop()
            }
        }
    }

    @Test
    fun testRandomClientsCallsV1() {
        testRandomClientsCalls(ClientType.V1)
    }

    @Test
    fun testRandomClientsCallsV2() {
        testRandomClientsCalls(ClientType.V2)
    }


    private fun testMultipleClientListen(clientType: ClientType) {
        runBlocking {
            var client: RelayClient? = null

            try {
                assertFails {
                    runBlocking {
                        client = getClient(clientType, "client0").also { launch { open(it) } }
                        open(client!!)
                    }
                }
            } finally {
                println("Closing resources")
                client?.close()
            }
        }
    }

    @Test
    fun testMultipleClientListenV1() {
        testMultipleClientListen(ClientType.V1)
    }

    @Test
    fun testMultipleClientListenV2() {
        testMultipleClientListen(ClientType.V2)
    }

    // @Test
    private fun testLatency(clientType: ClientType) {
        runBlocking {
            val server = startWebServer(defaultOCRPort)
            val client0 = getClient(
                clientType,
                "client0",
                requestTimeoutInMilliseconds = 30000
            ).also { launch { open(it) }; it.waitUntilOpen() }
            val client1 = getClient(clientType, "client1").also { launch { open(it) }; it.waitUntilOpen() }
            val data = ByteArray(1024 * 1024)

            try {
                (0..1).forEach { _ ->
                    val startTime = System.currentTimeMillis()
                    val response = client0.sendMessage(client1.publicKey, MessageStorageKey.unique(), data)
                    val elapsedTime = System.currentTimeMillis() - startTime
                    println("Elapsed time: ${elapsedTime / 1000}")
                    assertNotNull(response)
                }
            } finally {
                client0.close()
                client1.close()
                server.stop()
            }
        }
    }

    private fun testSendSingleMessageToMultipleRecipients(relayServerUri: URI) {
        runBlocking {
            var server: RelayServer? = null
            var clients: List<AbstractClient>? = null
            val results = Channel<String>()

            try {
                StdoutMonitor(readTimeoutMilliseconds = 3000).use { monitor ->
                    println("Starting RelayServer")
                    server = if (relayServerUri == localRelayServerUri) RelayServer().also { it.start() } else null

                    clients = (0..2).map { i ->
                        println("Starting client$i")
                        getClient(ClientType.V2, "client$i", relayServerUri = relayServerUri).also {
                            launch { it.open { _, m -> results.send("$i:${String(m)}") } }
                            it.also { it.waitUntilOpen() }
                        }
                    }

                    val sender = clients!![0]
                    val receivers = clients!!.subList(1, clients!!.size).map { it.publicKey }
                    println("Sending multi-recipient message")
                    sender.sendMessage(receivers, MessageStorageKey.none, "hello".toByteArray())

                    if (relayServerUri == localRelayServerUri)
                        monitor.waitUntil("Handling message from: .* to 2 recipients")

                    assertEquals(setOf("1:hello", "2:hello"), setOf(results.receive(), results.receive()))
                }
            } finally {
                println("Closing resources")
                server?.stop()
                clients?.forEach { it.close() }
            }
        }
    }

    @Test
    fun testSendSingleMessageToMultipleRecipients() {
        testSendSingleMessageToMultipleRecipients(localRelayServerUri)
    }

    // @Test
    fun testProd() {
        println("testSendResponseV1($prodRelayServerUri)")
        testSendResponse(ClientType.V1, prodRelayServerUri)
        println("testSendResponseV2($prodRelayServerUri)")
        testSendResponse(ClientType.V2, prodRelayServerUri)
        println("testClientConnectBeforeServerV1($prodRelayServerUri)")
        testClientPartition(ClientType.V1, prodRelayServerUri)
        println("testClientConnectBeforeServerV2($prodRelayServerUri)")
        testClientPartition(ClientType.V2, prodRelayServerUri)
        println("testServerPartitionV1($prodRelayServerUri)")
        testSendSingleMessageToMultipleRecipients(prodRelayServerUri)
    }

}