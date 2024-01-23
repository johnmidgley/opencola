/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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

    override fun flush() {
        if (outStream.size() > 0)
            queue.add(outStream.toString())
        super.flush()
    }

    override fun close() {
        flush()
        outStream.close()
    }

    fun readLine(): String? {
        return queue.poll()
    }

    fun waitForLine(timeoutMilliseconds: Long? = null): String {
        return if (timeoutMilliseconds == null)
            queue.take()
        else
            queue.poll(timeoutMilliseconds, TimeUnit.MILLISECONDS)
                ?: throw RuntimeException("Timeout waiting for line")
    }
}