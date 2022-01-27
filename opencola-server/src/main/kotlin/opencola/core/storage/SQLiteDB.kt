package opencola.core.storage

import opencola.core.config.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.nio.file.Path
import java.sql.Connection

// https://sqlitebrowser.org/dl/#macos
class SQLiteDB(val storagePath: Path) {
    private fun getDB(): Database {
        val db = Database.connect("jdbc:sqlite:$storagePath", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        return db
    }

    val db by lazy {
        getDB()
    }
}