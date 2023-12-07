package io.opencola.relay.common.connection

import io.opencola.model.Id
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class MemoryConnectionDirectory(override val localAddress: URI) : ConnectionDirectory {
    private val connections = ConcurrentHashMap<Id, ConnectionEntry>()

    override fun add(connection: Connection): ConnectionEntry {
        return ConnectionEntry(localAddress, connection).also { connections[connection.id] = it }
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

    override fun getLocalConnections(): Sequence<ConnectionEntry> {
        return connections.values.asSequence()
    }
}