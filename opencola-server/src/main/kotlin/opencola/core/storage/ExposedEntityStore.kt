package opencola.core.storage

import opencola.core.extensions.ifNotNullOrElse
import opencola.core.model.*
import opencola.core.security.Signator
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream

// TODO: Think about using SQLite - super simple and maybe better fit for local use.

class ExposedEntityStore(authority: Authority, addressBook: AddressBook, signator: Signator, private val database: Database) : AbstractEntityStore(authority, addressBook, signator) {
    // NOTE: Some databases may truncate the table name. This is an issue to the degree that it increases the
    // chances of collisions. Given the number of ids stored in a single DB, the chances of issue are exceedingly low.
    // This would likely be an issue only when storing data for large sets of users (millions to billions?)
    // TODO: Magic numbers (32, 128) should come from config
    // TODO: Normalize attribute
    private class Facts(authorityId: Id) : Table("fct-${authorityId}") {
        val authorityId = binary("authorityId", 32)
        val entityId = binary("entityId", 32).index()
        val attribute = text("attribute")
        val value = blob("value")
        val operation = enumeration("operation", Operation::class)
        val transactionId = binary("transactionId", 32)
    }

    // LongIdTable has implicit, autoincrement long id field
    private class Transactions(authorityId: Id) : LongIdTable("txs-${authorityId}") {
        val transactionId = binary("transactionId", 32).uniqueIndex()
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
        }
    }

    private fun getLastTransactionRow(authorityId: Id): ResultRow? {
        // TODO: Better way to get max?
        return transaction(database) {
            transactions.select { (transactions.authorityId eq Id.encode(authorityId)) }
                .orderBy(transactions.id to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }
    }

    override fun getLastTransactionId(authorityId: Id): Id? {
        return getLastTransactionRow(authorityId).ifNotNullOrElse(
            { Id.decode(it[transactions.transactionId]) },
            { null }
        )
    }

    override fun getNextTransactionId(authorityId: Id) : Id {
        return getLastTransactionRow(authorityId).ifNotNullOrElse(
            { Id.ofData(it[transactions.encoded].bytes) },
            { getFirstTransactionId(authorityId) }
        )
    }

    override fun resetStore(): EntityStore {
        transaction(database){
            SchemaUtils.drop(facts, transactions)
        }

        return ExposedEntityStore(authority, addressBook, signator, database)
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
                    Id.decode(it[facts.transactionId]))
            }

            if(facts.isNotEmpty()) Entity.getInstance(facts) else null
        }
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) : SignedTransaction {
        val transaction = signedTransaction.transaction

        transaction(database){
            transaction.expandFacts().forEach{ fact ->
                facts.insert {
                    it[authorityId] = Id.encode(fact.authorityId)
                    it[entityId] = Id.encode(fact.entityId)
                    it[attribute] = fact.attribute.uri.toString()
                    it[value] = ExposedBlob(fact.value.bytes)
                    it[operation] = fact.operation
                    it[transactionId] = Id.encode(fact.transactionId!!)
                }
            }

            transactions.insert {
                it[transactionId] = Id.encode(transaction.id)
                it[authorityId] = Id.encode(transaction.authorityId)
                it[epochSecond] = transaction.epochSecond
                it[encoded] = ExposedBlob(SignedTransaction.encode(signedTransaction))
            }
        }

        return signedTransaction
    }

    override fun getTransactions(authorityId: Id, startTransactionId: Id?, numTransactions: Int): Iterable<SignedTransaction> {
        return transaction(database){
            val op =
                if(startTransactionId == null)
                    (transactions.id eq 0)
                else
                    (transactions.transactionId eq Id.encode(startTransactionId))

            val startTransaction =
                    transactions.select {
                        (transactions.authorityId eq Id.encode(authority.authorityId) and op)
                    }.firstOrNull()

            if(startTransaction == null)
                emptyList()
            else
                transactions.select{ (transactions.id greaterEq  startTransaction[transactions.id]) }
                    .orderBy(transactions.id to SortOrder.ASC)
                    .limit(numTransactions)
                    .map{ row ->
                        ByteArrayInputStream(row[transactions.encoded].bytes).use {
                            SignedTransaction.decode(it)
                        }
                    }
                    .toList()
        }
    }
}