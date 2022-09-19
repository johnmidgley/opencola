package io.opencola.core.io

import java.io.PrintStream

fun readStdOut(echo: Boolean = true, until: (String) -> Boolean) {
    val lineBasedOutputStream = LinePartitionedOutputStream()
    val printStream = PrintStream(lineBasedOutputStream)
    val stdout = System.out

    try{
        System.setOut(printStream)
        while (true) {
            val line = lineBasedOutputStream.getLine()
            if(echo)
                stdout.print(line)
            if(until(line))
                break
        }
    } finally {
        System.setOut(stdout)
    }
}

class StdoutMonitor(private val echo: Boolean = true) {
    private val lineBasedOutputStream = LinePartitionedOutputStream()
    private val printStream = PrintStream(lineBasedOutputStream)
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

    fun readUntil(until: (String) -> Boolean) {
        while (true) {
            val line = lineBasedOutputStream.getLine()
            if(echo)
                stdout.print(line)
            if(until(line))
                break
        }
    }

    fun close() {
        System.setOut(stdout)
    }
}