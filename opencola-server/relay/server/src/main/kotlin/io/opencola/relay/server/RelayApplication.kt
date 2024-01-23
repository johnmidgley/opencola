/*
 * Copyright 2024 OpenCola
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

package io.opencola.relay.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.websocket.*
import io.opencola.event.log.EventLogger
import io.opencola.relay.common.connection.ExposedConnectionDirectory
import io.opencola.relay.common.message.v2.store.ExposedMessageStore
import io.opencola.relay.common.policy.ExposedPolicyStore
import io.opencola.storage.db.getSQLiteDB
import io.opencola.relay.server.plugins.configureRouting
import io.opencola.storage.filestore.FileSystemContentAddressedFileStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.slf4j.event.Level
import java.nio.file.Files
import java.nio.file.Path
import io.opencola.relay.server.v1.WebSocketRelayServer as WebSocketRelayServerV1
import io.opencola.relay.server.v2.WebSocketRelayServer as WebSocketRelayServerV2
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

private val logger = KotlinLogging.logger("relay")

fun startWebServer(
    config: RelayConfig,
    eventLogger: EventLogger,
    webSocketRelayServerV1: WebSocketRelayServerV1,
    webSocketRelayServerV2: WebSocketRelayServerV2,
    wait: Boolean = false
): NettyApplicationEngine {
    require(webSocketRelayServerV1.address == webSocketRelayServerV2.address) { "WebSocketRelayServer addresses must match" }
    val address = webSocketRelayServerV1.address
    val startSemaphore = Semaphore(1).also { it.acquire() }

    val module: Application.() -> Unit = {
        install(WebSockets) {
            // TODO: Check these values
            // pingPeriod = null
            // timeout = Duration.ofHours(1)
            maxFrameSize = config.capacity.maxPayloadSize
            masking = false

        }
        configureRouting(webSocketRelayServerV1, webSocketRelayServerV2)

        if(config.server.callLogging) {
            install(CallLogging) {
                level = Level.INFO
            }
        }

        this.environment.monitor.subscribe(ApplicationStarted) {
            eventLogger.log("RelayStarted", mapOf("address" to address.toString()))
            startSemaphore.release()
        }
    }

    thread {
        runBlocking {
            launch {
                webSocketRelayServerV1.open()
            }
            launch {
                webSocketRelayServerV2.open()
            }
        }
    }

    val server = embeddedServer(Netty, port = address.port, host = "0.0.0.0", module = module).start(wait)

    startSemaphore.acquire()
    return server
}

fun main() {
    val context = RelayContext(Path.of(System.getProperty("user.dir")))
    logger.info { context }

    // TODO: Add dependency injection
    val eventsPath = context.absoluteStoragePath.resolve("events").also { Files.createDirectories(it) }
    val eventLogger = EventLogger("relay", eventsPath)
    val relayDB = getSQLiteDB(context.absoluteStoragePath.resolve("relay.db"))
    val policyStore = ExposedPolicyStore(relayDB, context.config.security.rootId)
    val fileStore = FileSystemContentAddressedFileStore(context.absoluteStoragePath.resolve("messages"))
    val serverV1 = WebSocketRelayServerV1(context.config, eventLogger, context.address)
    val serverV2 = WebSocketRelayServerV2(
        context.config,
        eventLogger,
        policyStore,
        ExposedConnectionDirectory(relayDB, context.address),
        ExposedMessageStore(relayDB, context.config.capacity.maxBytesStored, fileStore, policyStore),
    )

    startWebServer(context.config, eventLogger, serverV1, serverV2, wait = true)
}