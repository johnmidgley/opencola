package opencola.core.io

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