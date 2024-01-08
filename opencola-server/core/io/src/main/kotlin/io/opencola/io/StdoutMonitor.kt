package io.opencola.io

import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.io.PrintStream
import kotlin.concurrent.thread

class StdoutMonitor(private val echo: Boolean = true, private val readTimeoutMilliseconds: Long? = null) : Closeable {
    private val linePartitionedOutputStream = LinePartitionedOutputStream()
    private val printStream = PrintStream(linePartitionedOutputStream)
    private val stdout: PrintStream = System.out

    init {
        System.setOut(printStream)
    }

    fun waitUntil(timeoutMilliseconds: Long? = null, prefix: String = "!", until: (String) -> Boolean) {
        while (true) {
            val line = linePartitionedOutputStream.waitForLine(timeoutMilliseconds ?: readTimeoutMilliseconds)
            if (echo)
            // The prefix is used to make it easy to tell what the monitor is processing
                stdout.println("$prefix$line")
            if (until(line))
                break
        }
    }

    fun waitUntil(until: String, timeoutMilliseconds: Long? = null) {
        waitUntil(timeoutMilliseconds) { it.contains(Regex(until)) }
    }

    fun flush() {
        while (true) {
            val line = linePartitionedOutputStream.readLine() ?: break
            if (echo)
                stdout.println(line)
        }
    }

    // Threads and coroutines don't mix well, so we use a thread to run coroutines that needs to run independent of threads
    fun runCoroutine(block: suspend CoroutineScope.() -> Unit) {
        thread {
            runBlocking { block() }
        }
    }

    override fun close() {
        flush()
        linePartitionedOutputStream.close()
        printStream.close()
        System.setOut(stdout)
    }
}

fun waitForStdout(until: String, timeoutMilliseconds: Long? = 5000, block: (() -> Unit)? = null) {
    StdoutMonitor().use {
        block?.invoke()
        it.waitUntil(until, timeoutMilliseconds)
    }
}