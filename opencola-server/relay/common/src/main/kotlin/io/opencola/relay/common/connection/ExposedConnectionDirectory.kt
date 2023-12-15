package io.opencola.relay.common.connection

import io.opencola.model.Id
import org.jetbrains.exposed.sql.Database
import java.net.URI

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