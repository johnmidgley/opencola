package io.opencola.storage

import io.opencola.application.TestApplication
import io.opencola.storage.db.getSQLiteDB
import org.jetbrains.exposed.sql.Database

fun newSQLiteDB(name: String): Database {
    val dbDirectory = TestApplication.getTmpDirectory("-db")
    return getSQLiteDB(dbDirectory.resolve("$name.db"))
}