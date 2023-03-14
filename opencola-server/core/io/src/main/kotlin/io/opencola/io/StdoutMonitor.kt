package io.opencola.io

import io.ktor.utils.io.core.*
import java.io.PrintStream

class StdoutMonitor(private val echo: Boolean = true, private val readTimeoutMilliseconds: Long? = null) : Closeable {
    private val linePartitionedOutputStream = LinePartitionedOutputStream()
    private val printStream = PrintStream(linePartitionedOutputStream)
    private val stdout: PrintStream = System.out

    init {
        System.setOut(printStream)
    }

    @Synchronized
    fun println(message: Any?) {
        val output = message.toString()
        printStream.println(output)
        flush()
    }

    fun waitUntil(timeoutMilliseconds: Long? = null, until: (String) -> Boolean) {
        while (true) {
            val line = linePartitionedOutputStream.waitForLine(timeoutMilliseconds ?: readTimeoutMilliseconds)
            if(echo)
                stdout.println(line)
            if(until(line))
                break
        }
    }

    fun waitUntil(until: String, timeoutMilliseconds: Long? = null) {
        waitUntil(timeoutMilliseconds) { it.contains(until) }
    }

    fun flush() {
        while (true) {
            val line = linePartitionedOutputStream.readLine() ?: break
            if (echo)
                stdout.println(line)
        }
    }

    override fun close() {
        flush()
        linePartitionedOutputStream.close()
        printStream.close()
        System.setOut(stdout)
    }
}