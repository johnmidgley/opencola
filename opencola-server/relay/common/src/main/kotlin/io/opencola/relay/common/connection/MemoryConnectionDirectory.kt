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

package io.opencola.relay.common.connection

import io.opencola.model.Id
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * In memory ConnectionDirectory (no persistence)
 */
class MemoryConnectionDirectory(override val localAddress: URI) : ConnectionDirectory {
    private val connections = ConcurrentHashMap<Id, ConnectionEntry>()

    override fun add(connection: Connection): ConnectionEntry {
        return ConnectionEntry(connection.id, localAddress, connection).also { connections[connection.id] = it }
    }

    override fun get(id: Id): ConnectionEntry? {
        return connections[id]
    }

    override fun remove(id: Id) {
        connections.remove(id)
    }

    override fun size(): Long {
        return connections.size.toLong()
    }

    override fun closeAll() {
        runBlocking {
            connections.values.forEach { it.connection!!.close() }
            connections.clear()
        }
    }

    override fun getConnections(): Sequence<ConnectionEntry> {
        return connections.values.asSequence()
    }
}