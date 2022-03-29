package opencola.core.messaging

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import opencola.core.extensions.toHexString
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.IllegalArgumentException
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.SynchronousQueue


// Local, durable queue: https://shaolang.github.io/posts/2020-04-26-getting-started-with-chronicle-queue/
// Sending remote messages: https://zeromq.org/get-started/?language=java#

// ***** IMPORTANT *****
// https://chronicle.software/chronicle-support-java-17/

class MessageBus(private val storagePath: Path, private val reactor: Reactor) {
    private val logger = KotlinLogging.logger("MessageBus")
    private val messageBatchSize = 100
    private var queue = LinkedBlockingDeque<Event>(1000) // TODO: Config
    private var isRunning = false
    private val messages = Messages()
    private val db by lazy {
        val db = Database.connect("jdbc:sqlite:$storagePath/message-bus.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        db
    }

    init{
        transaction(db) {
            SchemaUtils.create(messages)
        }
    }

    private enum class Event {
        MESSAGES_AVAILABLE,
        STOP,
    }

    private class Messages() : LongIdTable("messages") {
        val name = text("name")
        val body = blob("body")
        val epochSecond = long("epochSecond")
    }

    class Message(val id: Long, val name: String, val body: ByteArray, val epochSecond: Long = Instant.now().epochSecond){
        override fun toString(): String {
            return "Message(id=${this.id}, name=${this.name}, body=${body.toHexString()}, epochSecond=$epochSecond)"
        }
    }

    fun sendMessage(name: String, body: ByteArray){
        val epochSecond = Instant.now().epochSecond
        transaction(db){
            messages.insert {
                it[messages.name] = name
                it[messages.body] = ExposedBlob(body)
                it[messages.epochSecond] = epochSecond
            } get messages.id
        }

        queue.put(Event.MESSAGES_AVAILABLE)
    }

    private fun getMessagesFromStore() : List<Message> {
        return transaction(db){
            messages
                .selectAll()
                .orderBy(messages.id, SortOrder.ASC)
                .limit(messageBatchSize)
                .map {
                    Message(it[messages.id].value, it[messages.name], it[messages.body].bytes, it[messages.epochSecond])
                }
        }
    }

    private fun deleteMessageFromStore(id: Long){
        transaction(db){
            messages.deleteWhere { messages.id eq id }
        }
    }

    // Seems better to not bleed async nature of this to any users. It's a background process,
    // so starting a daemon thread makes sense.
    fun startReactor() {
        logger.info { "Starting reactor with queue at: $storagePath" }

        Thread {
            logger.info { "Reactor daemon started" }
            isRunning = true

            while (isRunning) {
                when (queue.take()) {
                    Event.MESSAGES_AVAILABLE -> {
                        val messages = getMessagesFromStore()

                        messages.forEach {
                            if (isRunning) {
                                try {
                                    reactor.handleMessage(it)
                                } catch (e: Exception) {
                                    logger.error { "Exception occurred processing $it: ${e.message}" }
                                }
                                deleteMessageFromStore(it.id)
                            }
                        }

                        if (messages.count() == messageBatchSize) {
                            queue.put(Event.MESSAGES_AVAILABLE)
                        }
                    }
                    Event.STOP -> break
                    null -> logger.error { "Received null event in message queue" }
                }
            }
            logger.info { "Reactor stopped" }
        }.start()
    }

    fun stopReactor(){
        isRunning = false
        queue.put(Event.STOP)
    }
}