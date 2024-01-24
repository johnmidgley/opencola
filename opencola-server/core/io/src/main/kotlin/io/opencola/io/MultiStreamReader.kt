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