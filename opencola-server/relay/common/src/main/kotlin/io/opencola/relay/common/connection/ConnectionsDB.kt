package io.opencola.relay.common.connection

import io.opencola.model.Id
import mu.KotlinLogging
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

class ConnectionsDB(private val database: Database) {
    private val logger = KotlinLogging.logger("ConnectionsDB")

    class Connections(name: String = "Connections") : LongIdTable(name) {
        val from = binary("from", 32).uniqueIndex()
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
                it[from] = id.encoded()
                it[this.address] = address.toString()
                it[this.connectTimeMilliseconds] = connectTimeMilliseconds
            }
        }
    }

    fun updateConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        transaction(database) {
            connections.update({ connections.from eq id.encoded() }) {
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
            connections.select { connections.from eq id.encoded() }.firstOrNull()?.let {
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
            connections.deleteWhere { connections.from eq id.encoded() }
        }
    }
}