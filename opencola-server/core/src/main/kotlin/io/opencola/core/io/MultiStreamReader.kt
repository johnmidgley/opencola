package io.opencola.core.io

import java.io.Closeable
import java.io.InputStream

class MultiStreamReader(inputStreams: List<Pair<String, InputStream>>) : Closeable {
    constructor(vararg inputStream: InputStream) : this(inputStream.map { Pair("", it) })

    private val inputReaders = inputStreams.map { Pair(it.first, it.second.bufferedReader()) }

    fun readLine() : String? {
        val (name, reader) = inputReaders.toList().firstOrNull() { it.second.ready() } ?: return null
        return "${if (name == "") "" else "$name: "}${reader.readLine()}"
    }

    override fun close() {
        inputReaders.forEach {
            it.second.close()
        }
    }
}