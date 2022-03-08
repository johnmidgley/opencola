package opencola.core.storage

import opencola.core.model.*
import opencola.core.model.Transaction
import opencola.core.security.Signator
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream

// TODO: Think about using SQLite - super simple and maybe better fit for local use.

class ExposedEntityStore(authority: Authority, signator: Signator, private val database: Database) : EntityStore(authority, signator) {
    // NOTE: Some databases may truncate the table name. This is an issue to the degree that it increases the
    // chances of collisions. Given the number of ids stored in a single DB, the chances of issue are exceedingly low.
    // This would likely be an issue only when storing data for large sets of users (millions to billions?)
    // TODO: Magic numbers (32, 128) should come from config
    // TODO: Normalize attribute
    private class Facts(authorityId: Id) : Table("fct-${authorityId.toString()}"){
        val authorityId = binary("authorityId", 32)
        val entityId = binary("entityId", 32).index()
        val attribute = text("attribute")
        val value = blob("value")
        val operation = enumeration("operation", Operation::class)
        val transactionId = long("transactionId")
    }

    private class Transactions(authorityId: Id) : Table("txs-${authorityId.toString()}") {
        // TODO - Make id unique
        val id = long("id")
        val authorityId = binary("authorityId", 32)
        val epochSecond = long("epochSecond")
        val encoded = blob("encoded")
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

            setTransactiondId(
                transactions.selectAll()
                    .orderBy(transactions.id to SortOrder.DESC)
                    .limit(1).firstOrNull()
                    ?.getOrNull(transactions.id)
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

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return transaction(database){
            val facts = facts.select{
                (facts.authorityId eq Id.encode(authorityId) and (facts.entityId eq Id.encode(entityId)))
            }.map {
                Fact(Id.decode(it[facts.authorityId]),
                    Id.decode(it[facts.entityId]),
                    CoreAttribute.values().single { a -> a.spec.uri.toString() == it[facts.attribute]}.spec,
                    Value(it[facts.value].bytes),
                    it[facts.operation],
                    it[facts.transactionId])
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
                    it[transactionId] = fact.transactionId
                }
            }

            transactions.insert {
                it[id] = signedTransaction.transaction.id
                it[authorityId] = Id.encode(signedTransaction.transaction.authorityId)
                it[epochSecond] = signedTransaction.transaction.epochSecond
                it[encoded] = ExposedBlob(SignedTransaction.encode(signedTransaction))
            }
        }
    }

    // TODO: Remover getTransaction, or move to abstract entityStore
    override fun getTransaction(authorityId: Id, transactionId: Long): SignedTransaction? {
        return getTransactions(authorityId, transactionId, transactionId).firstOrNull()
    }

    override fun getTransactions(authorityId: Id, startTransactionId: Long, endTransactionId: Long): Iterable<SignedTransaction> {
        return transaction(database){
            transactions.select{
                (transactions.authorityId eq Id.encode(authority.authorityId)
                        and (transactions.id greaterEq startTransactionId)
                        and (transactions.id lessEq endTransactionId))
            }.map { row ->
                ByteArrayInputStream(row[transactions.encoded].bytes).use {
                    SignedTransaction.decode(it)
                }
            }.toList()
        }
    }
}