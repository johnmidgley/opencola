/*
 * Copyright 2024-2026 OpenCola
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

package io.opencola.relay.common.connection

import io.ktor.network.sockets.*
import io.ktor.network.sockets.Socket
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking

/**
 * Regular (non web) socket
 */
class StandardSocketSession(private val socket: Socket) : SocketSession {
    val maxReadSize = 1024 * 1024 * 50
    val readChannel = socket.openReadChannel()
    val writeChannel = socket.openWriteChannel(autoFlush = true)

    override suspend fun isReady(): Boolean {
        return !(socket.isClosed || readChannel.isClosedForRead || writeChannel.isClosedForWrite)
    }

    override suspend fun readSizedByteArray(): ByteArray {
        val numBytes = readChannel.readInt()

        if (numBytes > maxReadSize) {
            throw IllegalArgumentException("Read size to big: $numBytes")
        }

        return ByteArray(numBytes).also { readChannel.readFully(it, 0, it.size) }
    }

    override suspend fun writeSizedByteArray(byteArray: ByteArray) {
        writeChannel.writeInt(byteArray.size)
        writeChannel.writeFully(byteArray)
        writeChannel.flush()
    }

    override suspend fun close() {
        runBlocking { socket.close() }
    }
}