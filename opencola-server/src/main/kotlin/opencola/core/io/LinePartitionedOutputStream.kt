package opencola.core.io

import org.eclipse.jetty.util.BlockingArrayQueue
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class LinePartitionedOutputStream : OutputStream() {
    private var outStream = ByteArrayOutputStream()
    private val queue = BlockingArrayQueue<String>()

    override fun write(b: Int) {
        outStream.write(b)
        if (b.toChar() == '\n') {
            queue.add(outStream.toString())
            outStream = ByteArrayOutputStream()
        }
    }

    fun getLine() : String {
        return queue.take()
    }
}