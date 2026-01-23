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

import io.opencola.model.Id
import org.jetbrains.exposed.sql.Database
import java.net.URI

/**
 * ConnectionDirectory backed by an exposed database.
 */
class ExposedConnectionDirectory(database: Database, override val localAddress: URI) : ConnectionDirectory {
    private val localDirectory = MemoryConnectionDirectory(localAddress)
    private val connectionsDB = ConnectionsDB(database)

    override fun add(connection: Connection): ConnectionEntry {
        // Since this operation is not atomic, we make sure to add to the local directory first, to avoid
        // any local race condition. In particular, if we were to add to the DB first, it would be possible that
        // after the connection is written to the DB, but before it is added to the local directory, it could be looked
        // up by another caller, which would result in a null local connection. This actually happened in a test.
        val entry = localDirectory.add(connection)
        connectionsDB.upsertConnection(connection.id, localAddress, System.currentTimeMillis())
        return entry
    }

    override fun get(id: Id): ConnectionEntry? {
        return localDirectory.get(id)
            ?: connectionsDB.getConnection(id)?.let { connectionRow ->
                ConnectionEntry(
                    id,
                    URI(connectionRow.address),
                    null,
                    connectionRow.connectTimeMilliseconds
                )
            }
    }

    override fun remove(id: Id) {
        localDirectory.remove(id)
        connectionsDB.deleteConnection(id)
    }

    override fun size(): Long {
        return connectionsDB.countConnections()
    }

    override fun closeAll() {
        localDirectory.closeAll()
    }

    override fun getConnections(): Sequence<ConnectionEntry> {
        return connectionsDB.getConnections()
            .map {
                ConnectionEntry(
                    it.from,
                    URI(it.address),
                    localDirectory.get(it.from)?.connection,
                    it.connectTimeMilliseconds
                )
            }
    }
}