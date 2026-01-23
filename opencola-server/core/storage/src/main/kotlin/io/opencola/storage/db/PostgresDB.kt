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

package io.opencola.storage.db

import org.jetbrains.exposed.sql.Database

class PostgresDB(val host: String, val database: String, val user: String, val password: String, val port: Int = 5432) {
    val db by lazy {
        Database.connect(
            "jdbc:postgresql://$host:$port/$database",
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
    }
}

fun getPostgresDB(host: String, database: String, user: String, password: String, port: Int = 5432): Database {
    return PostgresDB(host, database, user, password, port).db
}
