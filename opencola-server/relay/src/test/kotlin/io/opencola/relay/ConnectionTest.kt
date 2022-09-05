package io.opencola.relay

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

fun cancelJobs(vararg jobs: Job){
    jobs.forEach { it.cancel() }
}

private const val defaultHost = "0.0.0.0"
private const val defaultPort = 5796

class ConnectionTest {
    class ClientController(private val name: String,
                           private val messageHandler: suspend (ByteArray) -> ByteArray = { _ -> name.toByteArray() },
                           val keyPair: KeyPair = generateKeyPair(),

                           ) : Closeable {
        val client: Client = Client(defaultHost, defaultPort, keyPair, name = name)
        private var clientJob: Job? = null

        suspend fun open() = coroutineScope {
            clientJob = launch { client.open(messageHandler) }
        }

        override fun close() {
            client.close()
            clientJob?.cancel()
        }
    }

    @Test
    fun testSendResponse() {
        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.open() }
            relayServer.waitUntilOpen()

            val client0Ctl = ClientController("client0").also { launch{ it.open() } ; it.client.waitUntilOpen() }
            val client1Ctl = ClientController("client1", { p -> p.append(" client1".toByteArray()) })
                .also { launch{ it.open() } ; it.client.waitUntilOpen() }

            val peerResponse = client0Ctl.client.sendMessage(client1Ctl.keyPair.public, "hello".toByteArray())
            assertNotNull(peerResponse)
            assertEquals("hello client1", String(peerResponse))

            client0Ctl.close()
            client1Ctl.close()
            cancelJobs(serverJob)
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

            relayServer.close()
            cancelJobs(client0Job, client1Job, serverJob)
        }
    }

    @Test
    fun testServerPartition() {
        runBlocking {
            val relayServer0 = RelayServer(defaultPort)
            val serverJob0 = launch { relayServer0.open() }
            relayServer0.waitUntilOpen()

            val keyPair0 = generateKeyPair()
            val client0 = Client("0.0.0.0", defaultPort, keyPair0)
            val client0Job = launch { client0.open { "client0".toByteArray() } }
            client0.waitUntilOpen()

            val keyPair1 = generateKeyPair()
            val client1 = Client("0.0.0.0", defaultPort, keyPair1)
            val client1Job = launch { client1.open { payload -> payload.append(" client1".toByteArray()) } }
            client1.waitUntilOpen()

            val peerResponse0 = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse0)
            assertEquals("hello client1", String(peerResponse0))

            relayServer0.close()
            serverJob0.cancel()

            val peerResponse1 = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNull(peerResponse1)

            val relayServer1 = RelayServer(defaultPort)
            val serverJob1 = launch { relayServer1.open() }
            relayServer1.waitUntilOpen()

            val peerResponse2 = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            relayServer1.close()
            cancelJobs(client0Job, client1Job, serverJob1)
        }
    }

    @Test
    fun testClientPartition() {
        runBlocking {
            println("Starting server and clients")
            val relayServer0 = RelayServer(defaultPort)
            val serverJob0 = launch { relayServer0.open() }
            relayServer0.waitUntilOpen()

            val keyPair0 = generateKeyPair()
            val client0 = Client("0.0.0.0", defaultPort, keyPair0)
            val client0Job = launch { client0.open { "client0".toByteArray() } }
            client0.waitUntilOpen()

            val keyPair1 = generateKeyPair()
            val client1 = Client("0.0.0.0", defaultPort, keyPair1)
            val client1Job = launch { client1.open { payload -> payload.append(" client1".toByteArray()) } }
            client1.waitUntilOpen()

            println("Sending message")
            val peerResponse0 = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse0)
            assertEquals("hello client1", String(peerResponse0))

            println("Partitioning client")
            client1.close()
            client1Job.cancel()

            println("Verifying partition")
            // TODO: Investigate the double connection error in logs at this point
            val peerResponse1 = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNull(peerResponse1)

            println("Rejoining client")
            val client1Rejoin = Client("0.0.0.0", defaultPort, keyPair1)
            val client1RejoinJob = launch { client1Rejoin.open { payload -> payload.append(" client1".toByteArray()) } }
            client1Rejoin.waitUntilOpen()

            println("Verifying rejoin")
            val peerResponse2 = client0.sendMessage(keyPair1.public, "hello".toByteArray())
            assertNotNull(peerResponse2)
            assertEquals("hello client1", String(peerResponse2))

            relayServer0.close()
            cancelJobs(client0Job, client1Job, client1RejoinJob, serverJob0)
        }
    }

    @Test
    fun testRandomClientsCalls() {
        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch { relayServer.open() }
            relayServer.waitUntilOpen()

            val numClients = 20
            val clientControllers = (0 until numClients).map { ClientController("client$it") }
            clientControllers.forEach { launch { it.open() }; it.client.waitUntilOpen() }

            val random = Random()

            (0..1000).map {
                val sender = abs(random.nextInt()) % numClients
                val receiver = abs(random.nextInt()) % numClients

                launch {
                    if(sender != receiver) {
                        val sendClient = clientControllers[sender]
                        val receiveClient = clientControllers[receiver]

                        val response = sendClient.client.sendMessage(receiveClient.keyPair.public, "hello".toByteArray())
                        assertNotNull(response)
                        assertEquals("client$receiver", String(response))
                    }
                }
            }.forEach { it.join() }

            clientControllers.forEach { it.close() }
            relayServer.close()
            serverJob.cancel()
        }
    }

    @Test
    fun testMultipleClientListen() {

    }
}