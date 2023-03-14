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
        readUntil { line ->
            line.dropLastWhile { c -> c == '\n' } == output
        }
    }

    fun readUntil(timeoutMilliseconds: Long? = null, until: (String) -> Boolean) {
        while (true) {
            val line = linePartitionedOutputStream.waitForLine(timeoutMilliseconds ?: readTimeoutMilliseconds)
            if(echo)
                stdout.print(line)
            if(until(line))
                break
        }
    }

    override fun close() {
        System.setOut(stdout)
    }
}