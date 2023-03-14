package io.opencola.io

import org.junit.Test
import kotlin.test.assertFails

class StdoutMonitorTest {
    @Test
    fun testStdoutMonitor() {
        StdoutMonitor(readTimeoutMilliseconds = 500).use {
            println("Hello World")
            it.waitUntil { line -> line == "Hello World" }
        }
    }

    @Test
    fun testStdoutMonitorTimeout() {
        assertFails {
            StdoutMonitor(readTimeoutMilliseconds = 500).use {
                it.waitUntil { line -> line == "Hello World" }
            }
        }
    }
}