package io.opencola.relay.cli

import io.opencola.model.Id
import io.opencola.model.IdAsStringSerializer
import io.opencola.relay.client.v2.WebSocketClient
import io.opencola.relay.common.message.v2.AdminMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    relayUri: URI
) : Closeable {
    private val _client = WebSocketClient(relayUri, keyPair, "OCR CLI")
    private var clientJob: Job? = null
    val responseChannel = Channel<AdminMessage>()
    val json: Json = Json { serializersModule = SerializersModule { contextual(Id::class, IdAsStringSerializer) } }

    val client by lazy {
        val semaphore = Semaphore(0)

        thread {
            runBlocking {
                clientJob = launch { _client.open { _, message -> responseChannel.send(AdminMessage.decode(message)) } }
                _client.waitUntilOpen()
                semaphore.release()
            }
        }

        semaphore.acquire()
        _client
    }

    override fun close() {
        runBlocking {
            _client.close()
        }
    }
}