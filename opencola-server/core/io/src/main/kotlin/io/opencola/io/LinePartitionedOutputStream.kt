package io.opencola.io

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class LinePartitionedOutputStream : OutputStream(), Closeable {
    private var outStream = ByteArrayOutputStream()
    private val queue = ArrayBlockingQueue<String>(100)

    override fun write(b: Int) {
        if (b.toChar() == '\n') {
            queue.add(outStream.toString())
            outStream.close()
            outStream = ByteArrayOutputStream()
        } else
            outStream.write(b)
    }

    fun waitForLine(timeoutMilliseconds: Long? = null): String {
        return if (timeoutMilliseconds == null)
            queue.take()
        else
            queue.poll(timeoutMilliseconds, TimeUnit.MILLISECONDS)
                ?: throw RuntimeException("Timeout waiting for line")
    }

    override fun close() {
        if (outStream.size() > 0) {
            queue.add(outStream.toString())
            outStream.close()
        }
    }
}