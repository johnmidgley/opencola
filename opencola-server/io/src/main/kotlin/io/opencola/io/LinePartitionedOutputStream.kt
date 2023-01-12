package io.opencola.io

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue

class LinePartitionedOutputStream : OutputStream() {
    private var outStream = ByteArrayOutputStream()

    private val queue = ArrayBlockingQueue<String>(100)

    override fun write(b: Int) {
        outStream.write(b)
        if (b.toChar() == '\n') {
            queue.add(outStream.toString())
            outStream = ByteArrayOutputStream()
        }
    }

    // TODO: Add a timeout here - likely need to set a timer callback that sets a "timedOut" flag and puts
    //  sentry value on the queue. getLine should then throw an exception on timedOut being set or the sentry value
    fun getLine() : String {
        return queue.take()
    }
}