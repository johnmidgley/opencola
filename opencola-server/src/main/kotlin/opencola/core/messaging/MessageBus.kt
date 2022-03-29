package opencola.core.messaging

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import java.time.Instant

// Local, durable queue: https://shaolang.github.io/posts/2020-04-26-getting-started-with-chronicle-queue/
// Sending remote messages: https://zeromq.org/get-started/?language=java#

// ***** IMPORTANT *****
// https://chronicle.software/chronicle-support-java-17/

class MessageBus(private val storagePath: Path, private val reactor: Reactor) {
    private val logger = KotlinLogging.logger("MessageBus")
    private val messageBatchSize = 100
    private val channel = Channel<Event>(UNLIMITED)
    private val db by lazy {
        val db = Database.connect("jdbc:sqlite:$storagePath/message-bus.db", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        db
    }

    private val messages = Messages()

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

    class Message(val id: Long, val name: String, val body: ByteArray, val epochSecond: Long = Instant.now().epochSecond)

    fun sendMessage(name: String, body: ByteArray){
        val epochSecond = Instant.now().epochSecond
        val messageId = transaction(db){
            messages.insert {
                it[messages.name] = name
                it[messages.body] = ExposedBlob(body)
                it[messages.epochSecond] = epochSecond
            } get messages.id
        }

        runBlocking {
            launch {
                channel.send(Event.MESSAGES_AVAILABLE)
            }
        }
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
            messages.deleteWhere {  messages.id eq id }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    //TODO: Change this. Seems better to not bleed async nature of this to any users. It's a background process,
    // so starting a daemon thread makes sense, and use a synchronized queue instead of a channel
    suspend fun startReactor() {
        logger.info { "Starting reactor with queue at: $storagePath" }

        while(true) {
            when(channel.receive()){
                Event.MESSAGES_AVAILABLE -> {
                    val messages = getMessagesFromStore()

                    messages.forEach{
                        reactor.handleMessage(it)
                        deleteMessageFromStore(it.id)
                    }

                    if(messages.count() == messageBatchSize) {
                        channel.send(Event.MESSAGES_AVAILABLE)
                    }
                }
                Event.STOP -> break
            }
        }

        logger.info { "Reactor stopped" }
    }

    fun stopReactor(){
        runBlocking {
            launch {
                channel.send(Event.STOP)
            }
        }
    }
}