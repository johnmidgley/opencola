package io.opencola.relay.common.connection

import io.opencola.model.Id
import java.net.URI

data class ConnectionEntry(
    val address: URI,
    val connection: Connection? = null,
    val connectTimeMilliseconds: Long = System.currentTimeMillis()
)

interface ConnectionDirectory {
    val localAddress: URI
    fun add(connection: Connection): ConnectionEntry
    fun get(id: Id): ConnectionEntry?
    fun getLocalConnections(): Sequence<ConnectionEntry>
    fun remove(id: Id)
    fun closeAll()
}