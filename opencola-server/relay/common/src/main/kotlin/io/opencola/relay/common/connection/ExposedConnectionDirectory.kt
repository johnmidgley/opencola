package io.opencola.relay.common.connection

import io.opencola.model.Id
import org.jetbrains.exposed.sql.Database
import java.net.URI

class ExposedConnectionDirectory(database: Database, override val localAddress: URI) : ConnectionDirectory {
    private val localDirectory = MemoryConnectionDirectory(localAddress)
    private val connectionsDB = ConnectionsDB(database)

    override fun add(connection: Connection): ConnectionEntry {
        connectionsDB.upsertConnection(connection.id, localAddress, System.currentTimeMillis())
        return localDirectory.add(connection)
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