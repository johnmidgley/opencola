package io.opencola.relay.common.connection

import io.opencola.model.Id
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

class ConnectionsDB(val database: Database) {
    private val logger = KotlinLogging.logger("ConnectionsDB")

    class Connections(name: String = "Connections") : LongIdTable(name) {
        val connectionId = binary("from", 32).uniqueIndex()
        val address = varchar("address", 255)
        val connectTimeMilliseconds = long("connectTimeMilliseconds")
    }

    private val connections = Connections()

    init {
        transaction(database) {
            SchemaUtils.create(connections)
        }
    }

    fun insertConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        transaction(database) {
            connections.insert {
                it[connectionId] = id.encoded()
                it[this.address] = address.toString()
                it[this.connectTimeMilliseconds] = connectTimeMilliseconds
            }
        }
    }

    fun updateConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        transaction(database) {
            connections.update({ connections.connectionId eq id.encoded() }) {
                it[this.address] = address.toString()
                it[this.connectTimeMilliseconds] = connectTimeMilliseconds
            }
        }
    }

    fun addConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        if(getConnection(id) == null)
            insertConnection(id, address, connectTimeMilliseconds)
        else {
            logger.warn { "Orphaned connection found: $id" }
            updateConnection(id, address, connectTimeMilliseconds)
        }
    }

    fun getConnection(id: Id): ConnectionEntry? {
        return transaction(database) {
            connections.select { connections.connectionId eq id.encoded() }.firstOrNull()?.let {
                ConnectionEntry(
                    URI(it[connections.address]),
                    null,
                    it[connections.connectTimeMilliseconds]
                )
            }
        }
    }

    fun deleteConnection(id: Id) {
        transaction(database) {
            connections.deleteWhere { connections.connectionId eq id.encoded() }
        }
    }
}