package opencola.core.storage

import org.jetbrains.exposed.sql.Database

object PostgresDb {
    val db by lazy {
        // TODO: Consider https://github.com/pgjdbc/pgjdbc from https://github.com/JetBrains/Exposed/wiki/DataBase-and-DataSource
        // TODO: Using postgres db, as default. Should create opencola
        Database.connect("jdbc:postgresql://localhost:5432/postgres", driver = "org.postgresql.Driver",
            user = "postgres", password = "example")
    }
}