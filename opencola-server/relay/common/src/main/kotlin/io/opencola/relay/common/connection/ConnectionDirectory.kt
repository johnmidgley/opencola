package io.opencola.relay.common.connection

import io.opencola.model.Id
import java.util.concurrent.ConcurrentHashMap

class ConnectionDirectory {
    private val connections = ConcurrentHashMap<Id, Connection>()

    fun add(connection: Connection) {
        connections[connection.id] = connection
    }

    fun get(id: Id): Connection? {
        return connections[id]
    }

    fun remove(connection: Connection) {
        connections.remove(connection.id)
    }

    suspend fun closeAll() {
        connections.values.forEach { it.close() }
        connections.clear()
    }

    suspend fun states(): List<Pair<String, Boolean>> {
        return connections.map { Pair(it.key.toString(), it.value.isReady()) }
    }
}