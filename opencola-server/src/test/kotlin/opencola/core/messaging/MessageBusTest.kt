package opencola.core.messaging

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import opencola.core.TestApplication
import opencola.core.messaging.MessageBus.*
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MessageBusTest{
    class MessageReactor : Reactor {
        var messages = emptyList<Message>()
        override fun handleMessage(message: Message) {
            messages = messages + message
        }


    }

    @Test
    fun testMessageBus(){
        val reactor = MessageReactor()
        val messageBus = MessageBus(TestApplication.createStorageDirectory("message-bus"), reactor)

        messageBus.sendMessage("1", "1".toByteArray())

        runBlocking {
            launch { messageBus.startReactor() }
            messageBus.sendMessage("2", "2".toByteArray())
            messageBus.stopReactor()
            messageBus.sendMessage("3", "3".toByteArray())
            launch { messageBus.startReactor() }
            messageBus.stopReactor()
        }

        assertEquals(3, reactor.messages.count())
        assertEquals("1", reactor.messages[0].name)
        assertContentEquals("1".toByteArray(), reactor.messages[0].body)
        assertEquals("2", reactor.messages[1].name)
        assertContentEquals("2".toByteArray(), reactor.messages[1].body)
        assertEquals("3", reactor.messages[2].name)
        assertContentEquals("3".toByteArray(), reactor.messages[2].body)
    }
}