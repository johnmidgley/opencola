package io.opencola.core.event


import mu.KotlinLogging
import io.opencola.core.config.EventBusConfig
import io.opencola.core.extensions.nullOrElse
import io.opencola.core.extensions.shutdownWithTimout
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.Executors

class EventBus(private val storagePath: Path, config: EventBusConfig) {
    private val logger = KotlinLogging.logger("MessageBus")
    private var reactor: Reactor? = null
    private val messages = Messages()
    private val maxAttempts = config.maxAttempts
    private val executorService = Executors.newSingleThreadExecutor()
    private val db by lazy {
        val db = Database.connect("jdbc:sqlite:$storagePath/${config.name}.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        db
    }

    init {
        transaction(db) {
            SchemaUtils.create(messages)
        }
    }

    private class Messages() : LongIdTable("messages") {
        val name = text("name")
        val data = blob("data")
        val attempt = integer("attempt")
        val epochSecond = long("epochSecond")
    }

    class Event(
        val id: Long,
        val name: String,
        val data: ByteArray,
        val attempt: Int = 0,
        val epochSecond: Long = Instant.now().epochSecond,
        ) {
        override fun toString(): String {
            return "Message(id=${this.id}, name=${this.name}, data=${data.size} bytes, attempt=$attempt, epochSecond=$epochSecond)"
        }
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

    fun sendMessage(name: String, data: ByteArray = "".toByteArray()) {
        if(reactor == null){
            throw IllegalStateException("Attempt to sendMessage without having set a reactor")
        }

        if(executorService.isShutdown) {
            logger.warn("Unable to process message $name - executor has shut down")
            return
        }

        addMessageToStore(name, data, 1)
        executorService.execute { processMessages() }
    }

    private fun processMessage(message: Event) {
        try {
            val reactor = this.reactor ?: throw IllegalStateException("Attempt to process a message with no reactor set")
            reactor.handleMessage(message)
        } catch (e: Exception) {
            logger.error { "Exception occurred processing $message: ${e.message}" }
            if(message.attempt < maxAttempts){
                addMessageToStore(message.name, message.data, message.attempt + 1)
            }
        }

        deleteMessageFromStore(message.id)
    }

    private fun processMessages() {
        val messages = getMessagesFromStore(2)

        messages.firstOrNull().nullOrElse {
            processMessage(it)

            // Process remaining messages. We don't loop here, to give the executor a chance to shut down
            // TODO: Fix - have started flag and loop here
            if (messages.count() > 1)
                executorService.execute { processMessages() }
        }
    }

    // Reactor is defined independently of EventBus in order to avoid circular dependencies
    fun start(reactor: Reactor) {
        if(this.reactor != null)
            throw IllegalStateException("Attempt to re-start event bus")

        this.reactor = reactor
    }

    // TODO: Call this on dispose / finalize?
    fun stop() {
        executorService.shutdownWithTimout(800)
    }
}