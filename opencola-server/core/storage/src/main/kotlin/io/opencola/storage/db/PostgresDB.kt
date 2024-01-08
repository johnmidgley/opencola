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
