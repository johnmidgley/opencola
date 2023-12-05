package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktError
import io.opencola.model.Id
import io.opencola.model.IdAsStringSerializer
import io.opencola.relay.client.v2.WebSocketClient
import io.opencola.relay.common.message.v2.AdminMessage
import io.opencola.relay.common.message.v2.CommandResponse
import io.opencola.relay.common.message.v2.State
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.Closeable
import java.net.URI
import java.nio.file.Path
import java.security.KeyPair
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class Context(
    val storagePath: Path,
    val keyPair: KeyPair,
    relayUri: URI,
    private val connectTimeoutMilliseconds: Long = 3000, // TODO: Make configurable
    private val requestTimeoutMilliseconds: Long = 5000, // TODO: Make configurable
) : Closeable {
    private val _client = WebSocketClient(relayUri, keyPair, "OCR CLI")
    private var clientJob: Job? = null
    val responseChannel = Channel<AdminMessage>()
    val json: Json = Json { serializersModule = SerializersModule { contextual(Id::class, IdAsStringSerializer) } }

    val client by lazy {
        val semaphore = Semaphore(0)
        var exception: Exception? = null

        thread {
            runBlocking {
                clientJob = launch { _client.open { _, message -> responseChannel.send(AdminMessage.decode(message)) } }

                try {
                    withTimeout(connectTimeoutMilliseconds) { _client.waitUntilOpen() }
                } catch (e: Exception) {
                    exception = e
                } finally {
                    semaphore.release()
                }
            }
        }

        semaphore.acquire()

        if (exception != null)
            throw CliktError("Error connecting to relay [$relayUri]: ${exception!!.message}")

        _client
    }

    fun sendCommandMessage(command: AdminMessage): AdminMessage {
        return runBlocking {
            try {
                withTimeout(requestTimeoutMilliseconds) {
                    client.sendAdminMessage(command)
                    var response: AdminMessage

                    while (true) {
                        response = responseChannel.receive()
                        if (response is CommandResponse && response.state == State.PENDING)
                            println(response.format())
                        else
                            break
                    }
                    response
                }
            } catch (e: CliktError) {
                throw e
            } catch (e: Exception) {
                throw CliktError("Error sending command: ${e.message}")
            }
        }
    }

    override fun close() {
        runBlocking {
            _client.close()
        }
    }
}