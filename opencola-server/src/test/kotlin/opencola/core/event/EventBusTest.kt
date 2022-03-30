package opencola.core.event

import opencola.core.TestApplication
import opencola.core.config.EventBusConfig
import opencola.core.event.EventBus.Event
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class EventBusTest{
    class MessageReactor : Reactor {
        var events = emptyList<Event>()
        override fun handleMessage(event: Event) {
            println("Handling $event")

            if(event.name == "EXPLODE"){
                throw RuntimeException("Exploded")
            }

            events = events + event
        }
    }

    @Test
    fun testEventBus(){
        val reactor = MessageReactor()
        val eventBus = EventBus(TestApplication.config.storage.path, EventBusConfig(), reactor)

        eventBus.sendMessage("1", "1".toByteArray())
        eventBus.sendMessage("EXPLODE", "".toByteArray())
        eventBus.sendMessage("2", "2".toByteArray())
        eventBus.sendMessage("3", "3".toByteArray())
        Thread.sleep(100)
        eventBus.stop()

        assertEquals(3, reactor.events.count())
        assertEquals("1", reactor.events[0].name)
        assertContentEquals("1".toByteArray(), reactor.events[0].data)
        assertEquals("2", reactor.events[1].name)
        assertContentEquals("2".toByteArray(), reactor.events[1].data)
        assertEquals("3", reactor.events[2].name)
        assertContentEquals("3".toByteArray(), reactor.events[2].data)
    }
}