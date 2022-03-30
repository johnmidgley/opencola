package opencola.core.messaging


import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.toHexString
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MessageBus(private val storagePath: Path, private val reactor: Reactor) {
    private val logger = KotlinLogging.logger("MessageBus")
    private val messages = Messages()
    private val maxAttempts = 3
    private val executorService = Executors.newSingleThreadExecutor()
    private val db by lazy {
        val db = Database.connect("jdbc:sqlite:$storagePath/message-bus.db", "org.sqlite.JDBC")
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
        val body = blob("body")
        val attempt = integer("attempt")
        val epochSecond = long("epochSecond")
    }

    class Message(
        val id: Long,
        val name: String,
        val body: ByteArray,
        val attempt: Int = 0,
        val epochSecond: Long = Instant.now().epochSecond,
        ) {
        override fun toString(): String {
            return "Message(id=${this.id}, name=${this.name}, body=${body.toHexString()}, attempt=$attempt, epochSecond=$epochSecond)"
        }
    }

    private fun addMessageToStore(name: String, body: ByteArray, attempt: Int){
        transaction(db) {
            messages.insert {
                it[messages.name] = name
                it[messages.body] = ExposedBlob(body)
                it[messages.attempt] = attempt
                it[messages.epochSecond] = Instant.now().epochSecond
            }
        }
    }

    private fun getMessagesFromStore(limit: Int): List<Message> {
        return transaction(db) {
            messages
                .select {
                    messages.attempt lessEq maxAttempts
                }
                .orderBy(messages.id, SortOrder.ASC)
                .limit(limit)
                .map {
                    Message(
                        it[messages.id].value,
                        it[messages.name],
                        it[messages.body].bytes,
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

    fun sendMessage(name: String, body: ByteArray) {
        addMessageToStore(name, body, 1)
        executorService.execute { processMessages() }
    }

    private fun processMessage(message: Message) {
        try {
            reactor.handleMessage(message)
        } catch (e: Exception) {
            logger.error { "Exception occurred processing $message: ${e.message}" }
            if(message.attempt < maxAttempts){
                addMessageToStore(message.name, message.body, message.attempt + 1)
            }
        }

        deleteMessageFromStore(message.id)
    }

    private fun processMessages() {
        val messages = getMessagesFromStore(2)

        messages.firstOrNull().nullOrElse {
            processMessage(it)

            // Process remaining messages. We don't loop here, to give the executor a chance to shut down
            if (messages.count() > 1)
                executorService.execute { processMessages() }
        }
    }

    // TODO: Call this on dispose / finalize?
    fun stop() {
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
        }
    }
}