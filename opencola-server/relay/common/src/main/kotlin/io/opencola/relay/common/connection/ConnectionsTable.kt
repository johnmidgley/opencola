/*
 * Copyright 2024-2026 OpenCola
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
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ResultRow

/**
 * Connections Table definition
 */
object Connections : LongIdTable() {
    val from = binary("from", 32).uniqueIndex()
    val address = varchar("address", 255)
    val connectTimeMilliseconds = long("connectTimeMilliseconds")
}

/**
 * Connection row wrapper that maps to model types
 */
class ConnectionRow(private val resultRow: ResultRow) {
    val from: Id by lazy { Id(resultRow[Connections.from]) }
    val address: String by lazy { resultRow[Connections.address] }
    val connectTimeMilliseconds: Long by lazy { resultRow[Connections.connectTimeMilliseconds] }
}