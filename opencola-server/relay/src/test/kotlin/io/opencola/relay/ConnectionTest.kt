package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.plugins.RelayServer
import kotlinx.coroutines.*
import kotlin.test.Test

class ConnectionTest {
    private val defaultPort = 5796

    @Test
    fun testConnection() {
        val keyPair = generateKeyPair()
        runBlocking {
            val relayServer = RelayServer(defaultPort)
            val serverJob = launch() { relayServer.run() }
            while(!relayServer.isStarted()){ delay(50) }

            val client = Client("0.0.0.0", defaultPort, keyPair)
            client.writeLine("hi")
            println("Response: ${client.readLine()}")
            serverJob.cancel()
        }
    }
}