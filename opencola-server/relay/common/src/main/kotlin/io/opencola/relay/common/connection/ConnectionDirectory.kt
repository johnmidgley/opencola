package io.opencola.relay.common.connection

import io.opencola.model.Id
import java.net.URI

data class ConnectionEntry(val address: URI, val connection: Connection)

interface ConnectionDirectory {
    fun add(connection: Connection) : ConnectionEntry
    fun get(id: Id) : ConnectionEntry?
    fun remove(id: Id)
    fun closeAll()
}