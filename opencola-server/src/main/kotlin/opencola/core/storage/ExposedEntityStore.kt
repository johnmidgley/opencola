package opencola.core.storage

import opencola.core.model.*
import opencola.core.security.Signator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
// TODO: Think about using SQLite - super simple and maybe better fit for local use.

class ExposedEntityStore(authority: Authority, signator: Signator, private val database: Database) : EntityStore(authority, signator) {
    // NOTE: Some databases may truncate the table name. This is an issue to the degree that it increases the
    // chances of collisions. Given the number of ids stored in a single DB, the chances of issue are exceedingly low.
    // This would likely be an issue only when storing data for large sets of users (millions to billions?)
    // TODO: Magic numbers (32, 128) should come from config
    private class Facts(authorityId: Id) : Table("fct-${authorityId.toString()}"){
        val authorityId = binary("authorityId", 32)
        val entityId = binary("entityId", 32)
        val attribute = text("attribute")
        val value = blob("value")
        val operation = enumeration("operation", Operation::class)
        val epoch = long("epoch")
    }

    private class Transactions(authorityId: Id) : Table("txs-${authorityId.toString()}") {
        val authorityId = binary("authorityId", 32)
        val signature = binary("signature", 128) // TODO: Add signature length?
        val epoch = long("epoch")
    }

    private val facts: Facts
    private val transactions: Transactions

    init {
        logger.info { "Initializing ExposedEntityStore {${database.url}}" }

        facts = Facts(authority.authorityId)
        transactions = Transactions(authority.authorityId)

        transaction(database) {
            SchemaUtils.create(facts)
            SchemaUtils.create(transactions)

            setEpoch(
                transactions.selectAll()
                    .orderBy(transactions.epoch to SortOrder.DESC)
                    .limit(1).firstOrNull()
                    ?.getOrNull(transactions.epoch)
                    ?: 0
            )
        }
    }


    override fun resetStore(): EntityStore {
        transaction(database){
            SchemaUtils.drop(facts, transactions)
        }

        return ExposedEntityStore(authority, signator, database)
    }

    override fun getEntity(authority: Authority, entityId: Id): Entity? {
        return transaction(database){
            val facts = facts.select{
                (facts.authorityId eq Id.encode(authority.authorityId) and (facts.entityId eq Id.encode(entityId)))
            }.map {
                Fact(Id.decode(it[facts.authorityId]),
                    Id.decode(it[facts.entityId]),
                    CoreAttribute.values().single { a -> a.spec.uri.toString() == it[facts.attribute]}.spec,
                    Value(it[facts.value].bytes),
                    it[facts.operation],
                    it[facts.epoch])
            }

            if(facts.isNotEmpty()) Entity.getInstance(facts) else null
        }
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        // val facts = signedTransaction.expandFacts()

        transaction(database){
            signedTransaction.expandFacts().forEach{ fact ->
                facts.insert {
                    it[authorityId] = Id.encode(fact.authorityId)
                    it[entityId] = Id.encode(fact.entityId)
                    it[attribute] = fact.attribute.uri.toString()
                    it[value] = ExposedBlob(fact.value.bytes)
                    it[operation] = fact.operation
                    it[epoch] = fact.transactionId
                }
            }

            transactions.insert {
                it[authorityId] = Id.encode(signedTransaction.transaction.authorityId)
                it[signature] = signedTransaction.signature
                it[epoch] = signedTransaction.transaction.id
            }
        }
    }
}