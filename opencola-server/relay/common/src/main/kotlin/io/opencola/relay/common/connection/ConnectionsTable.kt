package io.opencola.relay.common.connection

import io.opencola.model.protobuf.Model.Id
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

object Connections : LongIdTable() {
    val from = binary("from", 32).uniqueIndex()
    val address = varchar("address", 255)
    val connectTimeMilliseconds = long("connectTimeMilliseconds")
}

class ConnectionRow(private val resultRow: ResultRow) {
    val from: Id
        get() = Id.parseFrom(resultRow[Connections.from])

    val address: String
        get() = resultRow[Connections.address]

    val connectTimeMilliseconds: Long
        get() = resultRow[Connections.connectTimeMilliseconds]
}