/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.cli

import com.github.ajalt.clikt.core.CliktError
import io.opencola.io.*
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

    inline fun <reified T : AdminMessage> sendCommandMessage(command: AdminMessage): T {
        return runBlocking {
            try {
                withTimeout(config.ocr.server.requestTimeoutMilliseconds) {
                    client.sendAdminMessage(command)
                    var response: AdminMessage

                    while (true) {
                        response = responseChannel.receive()

                        if(response.id != command.id) {
                            println(colorize(Color.RED,"Received response with unexpected id: ${response.id}"))
                            continue
                        }

                        if (response is CommandResponse && response.state == State.PENDING) {
                            println(response.format())
                            continue
                        }

                        if(response is T)
                            break

                        println(colorize(Color.RED,"Expected ${T::class.simpleName} but received ${response::class.simpleName}:"))
                        print(colorize(Color.YELLOW, response.format()))
                    }

                    response as T
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