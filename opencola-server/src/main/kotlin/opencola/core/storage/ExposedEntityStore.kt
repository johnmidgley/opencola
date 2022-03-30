package opencola.core.storage

import opencola.core.event.EventBus
import opencola.core.model.*
import opencola.core.security.Signator
import opencola.core.storage.EntityStore.TransactionOrder
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream

// TODO: Think about using SQLite - super simple and maybe better fit for local use.

class ExposedEntityStore(authority: Authority, eventBus: EventBus, addressBook: AddressBook, signator: Signator, private val database: Database)
    : AbstractEntityStore(authority, eventBus, addressBook, signator) {
    // NOTE: Some databases may truncate the table name. This is an issue to the degree that it increases the
    // chances of collisions. Given the number of ids stored in a single DB, the chances of issue are exceedingly low.
    // This would likely be an issue only when storing data for large sets of users (millions to billions?)
    // TODO: Magic numbers (32, 128) should come from config
    // TODO: Normalize attribute
    private class Facts(authorityId: Id) : LongIdTable("fct-${authorityId}") {
        val authorityId = binary("authorityId", 32).index()
        val entityId = binary("entityId", 32).index()
        val attribute = text("attribute")
        val value = blob("value")
        val operation = enumeration("operation", Operation::class)
        val epochSecond = long("epochSecond")
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

    private fun Op<Boolean>.withTableIdOrdering(column: Column<EntityID<Long>>, id: Long?, ascending: Boolean): Op<Boolean> {
        return if(id == null)
            this
        else{
            if (ascending)
                this.and( column greaterEq id)
            else
                this.and(column lessEq id)
        }
    }

    private fun Op<Boolean>.withIdConstraint(column: Column<ByteArray>, ids: List<Id>): Op<Boolean>{
        return if(ids.isEmpty())
            this
        else
            this.and(ids
                .map { (column eq Id.encode(it)) }
                .reduce { acc, op -> acc.or(op) })

    }

    private fun transactionsByAuthoritiesQuery(authorityIds: List<Id>, id: Long?, order: TransactionOrder): Query {
        return transactions
            .select {
                (transactions.id greaterEq 0) // Not elegant, but avoids separate selectAll clause when no constraints provided
                    .withTableIdOrdering(transactions.id, id, order == TransactionOrder.Ascending)
                    .withIdConstraint(transactions.authorityId, authorityIds)
            }
            // TODO: order by transactions.id or transactions.epochSecond??
            .orderBy(transactions.id to if (order == TransactionOrder.Ascending) SortOrder.ASC else SortOrder.DESC)
    }

    private fun startRowQuery(
        authorityIds: List<Id>,
        startTransactionId: Id?,
        order: TransactionOrder
    ): Query {
        return if (startTransactionId == null)
            transactionsByAuthoritiesQuery(authorityIds, null, order).limit(1)
        else
            transactions.select { transactions.transactionId eq Id.encode(startTransactionId) }
    }

    private fun getTransactionRows(
        authorityIds: Iterable<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): List<ResultRow> {
        return transaction(database){
            val authorityIdList = authorityIds.toList()
            val startRow = startRowQuery(authorityIdList, startTransactionId, order).firstOrNull()

            if(startRow == null)
                emptyList()
            else {
                transactionsByAuthoritiesQuery(authorityIdList, startRow[transactions.id].value, order)
                    .limit(limit)
                    .toList()
            }

        }
    }

    override fun getSignedTransactions(
        authorityIds: Iterable<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): Iterable<SignedTransaction> {
        return getTransactionRows(authorityIds, startTransactionId, order, limit).map { row ->
            ByteArrayInputStream(row[transactions.encoded].bytes).use {
                SignedTransaction.decode(it)
            }
        }
    }

    override fun resetStore(): EntityStore {
        transaction(database){
            SchemaUtils.drop(facts, transactions)
        }

        return ExposedEntityStore(authority, eventBus, addressBook, signator, database)
    }

    private fun factFromResultRow(resultRow: ResultRow): Fact {
        return Fact(Id.decode(resultRow[facts.authorityId]),
            Id.decode(resultRow[facts.entityId]),
            CoreAttribute.values().single { a -> a.spec.uri.toString() == resultRow[facts.attribute]}.spec,
            Value(resultRow[facts.value].bytes),
            resultRow[facts.operation],
            resultRow[facts.epochSecond],
            Id.decode(resultRow[facts.transactionId]))
    }

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return transaction(database){
            val facts = facts.select{
                (facts.authorityId eq Id.encode(authorityId) and (facts.entityId eq Id.encode(entityId)))
            }.map { factFromResultRow(it) }

            if(facts.isNotEmpty()) Entity.getInstance(facts) else null
        }
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) : SignedTransaction {
        val transaction = signedTransaction.transaction

        transaction(database){
            transaction.getFacts().forEach{ fact ->
                facts.insert {
                    it[authorityId] = Id.encode(fact.authorityId)
                    it[entityId] = Id.encode(fact.entityId)
                    it[attribute] = fact.attribute.uri.toString()
                    it[value] = ExposedBlob(fact.value.bytes)
                    it[operation] = fact.operation
                    it[epochSecond] = fact.epochSecond!!
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

    override fun getFacts(authorityIds: Iterable<Id>, entityIds: Iterable<Id>): List<Fact> {
        return transaction(database) {
            facts.select {
                (facts.id greaterEq 0)
                    .withIdConstraint(facts.authorityId, authorityIds.toList())
                    .withIdConstraint(facts.entityId, entityIds.toList())
            }.map { factFromResultRow(it) }
        }
    }
}
