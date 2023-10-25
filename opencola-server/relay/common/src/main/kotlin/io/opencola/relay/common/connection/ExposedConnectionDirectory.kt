package io.opencola.relay.common.connection

import io.opencola.model.Id
import org.jetbrains.exposed.sql.Database
import java.net.URI

class ExposedConnectionDirectory(database: Database, val localUri: URI) : ConnectionDirectory {
    private val localDirectory = MemoryConnectionDirectory(localUri)
    private val connectionsDB = ConnectionsDB(database)

    override fun add(connection: Connection): ConnectionEntry {
        connectionsDB.addConnection(connection.id, localUri, System.currentTimeMillis())
        return localDirectory.add(connection)
    }

    override fun get(id: Id): ConnectionEntry? {
        return localDirectory.get(id) ?: connectionsDB.getConnection(id)
    }

    override fun remove(id: Id) {
        localDirectory.remove(id)
        connectionsDB.deleteConnection(id)
    }

    override fun closeAll() {
        localDirectory.closeAll()
    }

    override fun getLocalConnections(): Sequence<ConnectionEntry> {
        return localDirectory.getLocalConnections()
    }
}