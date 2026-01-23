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

package io.opencola.event.bus

import mu.KotlinLogging
import io.opencola.util.shutdownWithTimout
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ExposedEventBus(private val storagePath: Path, config: EventBusConfig) : EventBus {
    private val logger = KotlinLogging.logger("MessageBus")
    private var reactor: Reactor? = null
    private val messages = Messages()
    private val maxAttempts = config.maxAttempts
    private var executorService: ExecutorService? = null

    private val db by lazy {
        val db = Database.connect("jdbc:sqlite:$storagePath/event-bus.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        db
    }
    private var started = false

    init {
        transaction(db) {
            SchemaUtils.create(messages)
        }
    }

    override fun setReactor(reactor: Reactor) {
        this.reactor = reactor
    }

    // Reactor is defined independently of EventBus in order to avoid circular dependencies
    override fun start() {
        if (started) throw IllegalStateException("Event bus already started")
        this.reactor ?: throw IllegalStateException("Unable to start event bus without a reactor")
        executorService = Executors.newSingleThreadExecutor()
        started = true
        logger.info { "Started" }
    }

    // TODO: Call this on dispose / finalize?
    override fun stop() {
        if(!started)
            throw IllegalStateException("Event bus not started")
        executorService?.shutdownWithTimout(800)
        executorService = null
        started = false
        logger.info { "Stopped" }
    }

    private class Messages() : LongIdTable("messages") {
        val name = text("name")
        val data = blob("data")
        val attempt = integer("attempt")
        val epochSecond = long("epochSecond")
    }

    private fun addMessageToStore(name: String, data: ByteArray, attempt: Int){
        transaction(db) {
            messages.insert {
                it[messages.name] = name
                it[messages.data] = ExposedBlob(data)
                it[messages.attempt] = attempt
                it[messages.epochSecond] = Instant.now().epochSecond
            }
        }
    }

    private fun getMessagesFromStore(limit: Int): List<Event> {
        return transaction(db) {
            messages
                .select {
                    messages.attempt lessEq maxAttempts
                }
                .orderBy(messages.id, SortOrder.ASC)
                .limit(limit)
                .map {
                    Event(
                        it[messages.id].value,
                        it[messages.name],
                        it[messages.data].bytes,
                        it[messages.attempt],
                        it[messages.epochSecond],
                    )
                }
        }
    }

    private fun deleteMessageFromStore(id: Long) {
        transaction(db) {
            messages.deleteWhere { messages.id eq id }
        }
    }

    override fun sendMessage(name: String, data: ByteArray) {
        if(!started)
            throw IllegalStateException("Event bus not started")
        addMessageToStore(name, data, 1)
        executorService!!.execute { processMessages() }
    }

    private fun processMessage(message: Event) {
        try {
            val reactor = this.reactor ?: throw IllegalStateException("Attempt to process a message with no reactor set")
            reactor.handleMessage(message)
        } catch (e: Exception) {
            logger.error { "Exception occurred processing $message: $e" }
            if(message.attempt < maxAttempts){
                addMessageToStore(message.name, message.data, message.attempt + 1)
            }
        }

        deleteMessageFromStore(message.id)
    }

    private fun processMessages() {
        val messages = getMessagesFromStore(2)

        messages.firstOrNull()?.let {
            processMessage(it)

            // Process remaining messages. We don't loop here, to give the executor a chance to shut down
            // TODO: Fix - have started flag and loop here
            if (messages.count() > 1)
                executorService!!.execute { processMessages() }
        }
    }
}