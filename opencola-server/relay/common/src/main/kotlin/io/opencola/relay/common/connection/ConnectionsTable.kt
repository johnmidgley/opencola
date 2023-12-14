package io.opencola.relay.common.connection

import io.opencola.model.Id
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

object Connections : LongIdTable() {
    val from = binary("from", 32).uniqueIndex()
    val address = varchar("address", 255)
    val connectTimeMilliseconds = long("connectTimeMilliseconds")
}

class ConnectionRow(private val resultRow: ResultRow) {
    val from: Id by lazy { Id(resultRow[Connections.from]) }
    val address: String by lazy { resultRow[Connections.address] }
    val connectTimeMilliseconds: Long by lazy { resultRow[Connections.connectTimeMilliseconds] }
}