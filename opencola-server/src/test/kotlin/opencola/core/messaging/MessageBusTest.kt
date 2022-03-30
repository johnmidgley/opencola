package opencola.core.messaging

import opencola.core.TestApplication
import opencola.core.messaging.MessageBus.Message
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class MessageBusTest{
    class MessageReactor : Reactor {
        var messages = emptyList<Message>()
        override fun handleMessage(message: Message) {
            println("Handling $message")

            if(message.name == "EXPLODE"){
                throw RuntimeException("Exploded")
            }

            messages = messages + message
        }
    }

    @Test
    fun testMessageBus(){
        val reactor = MessageReactor()
        val messageBus = MessageBus(TestApplication.createStorageDirectory("message-bus"), reactor)

        messageBus.sendMessage("1", "1".toByteArray())
        messageBus.sendMessage("EXPLODE", "".toByteArray())
        messageBus.sendMessage("2", "2".toByteArray())
        messageBus.sendMessage("3", "3".toByteArray())
        Thread.sleep(100)
        messageBus.stop()

        assertEquals(3, reactor.messages.count())
        assertEquals("1", reactor.messages[0].name)
        assertContentEquals("1".toByteArray(), reactor.messages[0].body)
        assertEquals("2", reactor.messages[1].name)
        assertContentEquals("2".toByteArray(), reactor.messages[1].body)
        assertEquals("3", reactor.messages[2].name)
        assertContentEquals("3".toByteArray(), reactor.messages[2].body)
    }
}