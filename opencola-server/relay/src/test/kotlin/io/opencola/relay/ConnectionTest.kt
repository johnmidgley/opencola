package io.opencola.relay

import io.opencola.core.security.generateKeyPair
import io.opencola.relay.client.Client
import io.opencola.relay.server.plugins.RelayServer
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import kotlin.test.Test

class ConnectionTest {
    private val defaultPort = 5796
    private val executorService = Executors.newSingleThreadExecutor()

    init {
        var started = false

        executorService.execute{
            runBlocking {
                RelayServer(defaultPort) { started = true }.run()
            }
        }

        while(!started){
            Thread.sleep(50)
        }
    }

    @Test
    fun testConnection() {
        val keyPair = generateKeyPair()
        runBlocking {
            val client = Client("0.0.0.0", defaultPort, keyPair).also { it.connect() }
            client.writeLine("hi")
            println("Response: ${client.readLine()}")
        }
    }
}