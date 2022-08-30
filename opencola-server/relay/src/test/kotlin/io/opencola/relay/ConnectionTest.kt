package io.opencola.relay

import io.opencola.relay.server.runRelayServer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.Test

class ConnectionTest {
    private val defaultPort = 5796
    private val executorService = Executors.newSingleThreadExecutor()

    init {
        executorService.execute{ runRelayServer(defaultPort) }

        // Wait for server to be ready
        Thread.sleep(1000)
    }

    @Test
    fun testConnection() {
        runBlocking {
            val client = Client.connect("0.0.0.0", defaultPort)
            client.writeLine("hi")
            println("Response: ${client.readLine()}")
        }
    }
}