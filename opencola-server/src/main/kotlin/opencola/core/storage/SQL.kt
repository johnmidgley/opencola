package opencola.core.storage

import opencola.core.model.CoreAttribute
import opencola.core.model.Id
import opencola.core.model.Operation
import opencola.core.model.Value
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction

class Facts(authorityId: Id) : Table("Facts_{${authorityId.toString()}"){
    val authorityId = binary("authorityId", 32)
    val entityId = binary("entityId", 32)
    val attribute = text("attribute")
    val value = binary("value")
    val operation = enumeration("operation", Operation::class)
    val epoch = long("epoch")
}

class Transactions(authorityId: Id) : Table("Transactions_{${authorityId.toString()}") {
    val authorityId = binary("authorityId", 32)
    val signature = binary("signature") // TODO: Add signature length?
    val epoch = long("epoch")
}


object SQL {
    // TODO: Pre-create opencola in docker
    object DbSettings {
        val db by lazy {
            // TODO: Consider https://github.com/pgjdbc/pgjdbc from https://github.com/JetBrains/Exposed/wiki/DataBase-and-DataSource
            Database.connect("jdbc:postgresql://localhost:5432/opencola", driver = "org.postgresql.Driver",
                user = "postgres", password = "example")
        }
    }

    object Facts : Table() {
        val authorityId = binary("authorityId", 32)
        val entityId = binary("entityId", 32)
        val attribute = text("attribute")
        val value = binary("value")
        val operation = enumeration("operation", Operation::class)
        val epoch = long("epoch")
    }

    object Transactions : Table() {
        val authorityId = binary("authorityId", 32)
        val signature = binary("signature") // TODO: Add signature length?
        val epoch = long("epoch")
    }

    fun test(){
        DbSettings.db

        transaction {
            SchemaUtils.create(Facts)

            Facts.insert {
                it[authorityId] = Id.encode(Id.ofData("authorityId".toByteArray()))
                it[entityId] = Id.encode(Id.ofData("entityId".toByteArray()))
                it[attribute] = CoreAttribute.Name.spec.uri.toString()
                it[value] = Value("Name".toByteArray()).bytes
                it[operation] = Operation.Add
                it[epoch] = 1
            }

        }
    }
}