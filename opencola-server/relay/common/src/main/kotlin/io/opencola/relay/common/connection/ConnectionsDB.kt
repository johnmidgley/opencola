/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.relay.common.connection

import io.opencola.model.Id
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

/**
 * Wrapper around an Exposed database that maps between model level types and DB level types.
 */
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
            // This can happen if a client reconnects and the server wasn't aware the old connection was closed
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