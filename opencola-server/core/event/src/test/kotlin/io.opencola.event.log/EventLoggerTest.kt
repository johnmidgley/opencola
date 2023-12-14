package io.opencola.event.log

import io.opencola.application.TestApplication
import org.junit.Test
import kotlin.test.assertEquals

class EventLoggerTest {
    @Test
    fun testLogEvent() {
        val storagePath = TestApplication.getTmpDirectory("event-log")
        val eventLogger = EventLogger("test", storagePath)

        println("EventLog storagePath: file://$storagePath}")

        val event0 = eventLogger.log("test", mapOf("param" to "value"))
        val event1 = eventLogger.log("test1")
        val event2 = eventLogger.log("test", mapOf("param1" to "value1"))

        eventLogger.flush()

        val events = readEventLogEntries(storagePath).toList()
        assertEquals(3, events.size)
        assertEquals(event0, events[0])
        assertEquals(event1, events[1])
        assertEquals(event2, events[2])

        val counts = summarize(storagePath)

        counts.forEach{ (name, count) ->
            println("$name: $count")
        }

        assertEquals(2, counts.size)
        assertEquals(2, counts["test"])
        assertEquals(1, counts["test1"])
    }
}