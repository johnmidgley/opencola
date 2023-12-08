package io.opencola.relay.common.connection

import io.opencola.model.Id
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

class ConnectionsDB(private val database: Database) {
    private val logger = KotlinLogging.logger("ConnectionsDB")

    init {
        transaction(database) {
            SchemaUtils.create(Connections)
        }
    }

    fun insertConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        transaction(database) {
            Connections.insert {
                it[from] = id.encoded()
                it[this.address] = address.toString()
                it[this.connectTimeMilliseconds] = connectTimeMilliseconds
            }
        }
    }

    fun updateConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        transaction(database) {
            Connections.update({ Connections.from eq id.encoded() }) {
                it[this.address] = address.toString()
                it[this.connectTimeMilliseconds] = connectTimeMilliseconds
            }
        }
    }

    fun upsertConnection(id: Id, address: URI, connectTimeMilliseconds: Long) {
        if(getConnection(id) == null)
            insertConnection(id, address, connectTimeMilliseconds)
        else {
            logger.warn { "Orphaned connection found: $id" }
            updateConnection(id, address, connectTimeMilliseconds)
        }
    }

    fun getConnection(id: Id): ConnectionRow? {
        return transaction(database) {
            Connections.select { Connections.from eq id.encoded() }.firstOrNull()?.let {
                ConnectionRow(it)
            }
        }
    }

    fun getConnections(): kotlin.sequences.Sequence<ConnectionRow> {
        return transaction(database) {
            Connections.selectAll().map { ConnectionRow(it) }.asSequence()
        }
    }

    fun deleteConnection(id: Id) {
        transaction(database) {
            Connections.deleteWhere { from eq id.encoded() }
        }
    }

    fun countConnections() : Long {
        return transaction(database) {
            Connections.selectAll().count()
        }
    }
}