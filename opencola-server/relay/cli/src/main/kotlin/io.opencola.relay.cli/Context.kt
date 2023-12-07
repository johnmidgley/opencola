package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktError
import io.opencola.io.printlnErr
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
import java.nio.file.Path
import java.security.KeyPair
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class Context(
    val storagePath: Path,
    val config: Config,
    val keyPair: KeyPair,
) : Closeable {
    private val _client = WebSocketClient(config.ocr.server.uri, keyPair, "OCR CLI")
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
                    withTimeout(config.ocr.server.connectTimeoutMilliseconds) { _client.waitUntilOpen() }
                } catch (e: Exception) {
                    exception = e
                } finally {
                    semaphore.release()
                }
            }
        }

        semaphore.acquire()

        if (exception != null)
            throw CliktError("Error connecting to relay [${config.ocr.server.uri}]: ${exception!!.message}")

        _client
    }

    fun sendCommandMessage(command: AdminMessage): AdminMessage {
        return runBlocking {
            try {
                withTimeout(config.ocr.server.requestTimeoutMilliseconds) {
                    client.sendAdminMessage(command)
                    var response: AdminMessage

                    while (true) {
                        response = responseChannel.receive()

                        if(response.id != command.id) {
                            printlnErr("Received response with unexpected id: ${response.id}")
                            continue
                        }

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