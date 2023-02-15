package io.opencola.storage

import io.opencola.event.EventBus
import io.opencola.model.*
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.storage.EntityStore.TransactionOrder
import io.opencola.storage.EntityStore.TransactionOrder.*
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream

// TODO: Think about using SQLite - super simple and maybe better fit for local use.

class ExposedEntityStore(
    private val database: Database,
    signator: Signator,
    publicKeyProvider: PublicKeyProvider<Id>,
    eventBus: EventBus? = null,
) : AbstractEntityStore(signator, publicKeyProvider, eventBus) {
    // NOTE: Some databases may truncate the table name. This is an issue to the degree that it increases the
    // chances of collisions. Given the number of ids stored in a single DB, the chances of issue are exceedingly low.
    // This would likely be an issue only when storing data for large sets of users (millions to billions?)
    // TODO: Magic numbers (32, 128) should come from config
    // TODO: Normalize attribute
    private class Facts(name: String = "Facts") : LongIdTable(name) {
        val authorityId = binary("authorityId", 32).index()
        val entityId = binary("entityId", 32).index()
        val attribute = text("attribute")
        val value = blob("value")
        val operation = enumeration("operation", Operation::class)
        val epochSecond = long("epochSecond")
        val transactionOrdinal = long("transactionOrdinal")
    }

    // LongIdTable has implicit, autoincrement long id field
    private class Transactions(name: String = "Transactions") : LongIdTable(name) {
        val transactionId = binary("transactionId", 32).uniqueIndex()
        val authorityId = binary("authorityId", 32)
        val epochSecond = long("epochSecond").index()
        val encoded = blob("encoded")
    }

    private val facts: Facts
    private val transactions: Transactions


    init {
        logger.info { "Initializing ExposedEntityStore {${database.url}}" }

        // Prior to personas, the table names included the authority id. This is no longer makes sense, but we for
        // backwards compatibility, we support the old table names.
        // TODO: Legacy Support
        val tableNames = transaction(database) {
            val allTableNames = TransactionManager.current().db.dialect.allTablesNames()
            object {
                val facts = allTableNames.firstOrNull { it.startsWith("fct-") } ?: "Facts"
                val transactions = allTableNames.firstOrNull { it.startsWith("txs-") } ?: "Transactions"
            }
        }

        facts = Facts(tableNames.facts)
        transactions = Transactions(tableNames.transactions)

        transaction(database) {
            SchemaUtils.create(facts)
            SchemaUtils.create(transactions)
        }
    }

    // TODO: Ids are not the same as time - switch to time ordering?
    private fun Op<Boolean>.withLongColumnOrdering(
        column: Column<*>,
        value: Long?,
        ascending: Boolean
    ): Op<Boolean> {
        return if (value == null)
            this
        else {
            if (ascending)
                this.and(column greaterEq value)
            else
                this.and(column lessEq value)
        }
    }

    private fun Op<Boolean>.withIdConstraint(column: Column<ByteArray>, ids: List<Id>): Op<Boolean> {
        return if (ids.isEmpty())
            this
        else
            this.and(ids
                .map { (column eq Id.encode(it)) }
                .reduce { acc, op -> acc.or(op) })

    }

    private fun getOrderColumn(order: TransactionOrder): Column<*> {
        return when(order){
            IdAscending -> transactions.id
            IdDescending -> transactions.id
            TimeAscending -> transactions.epochSecond
            TimeDescending -> transactions.epochSecond
        }
    }

    private fun isAscending(order: TransactionOrder): Boolean {
        return when (order){
            IdAscending -> true
            IdDescending -> false
            TimeAscending -> true
            TimeDescending -> false
        }
    }

    private fun transactionsByAuthoritiesQuery(authorityIds: List<Id>, id: Long?, order: TransactionOrder): Query {
        val orderColumn = getOrderColumn(order)
        val isAscending = isAscending(order)

        return transactions
            .select {
                (orderColumn greaterEq 0) // Not elegant, but avoids separate selectAll clause when no constraints provided
                    .withLongColumnOrdering(orderColumn, id, isAscending)
                    .withIdConstraint(transactions.authorityId, authorityIds)
            }
            // TODO: order by transactions.id or transactions.epochSecond??
            .orderBy(orderColumn to if (isAscending) SortOrder.ASC else SortOrder.DESC)
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

    private fun getStartValue(order: TransactionOrder, row: ResultRow): Long {
        return when(order){
            IdAscending -> row[transactions.id].value
            IdDescending -> row[transactions.id].value
            TimeAscending -> row[transactions.epochSecond]
            TimeDescending -> row[transactions.epochSecond]
        }
    }
    private fun getTransactionRows(
        authorityIds: Iterable<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): List<ResultRow> {
        return transaction(database) {
            val authorityIdList = authorityIds.toList()
            // TODO: There's likely a seam problem lurking here when ordering by time,
            //  since time isn't unique. Solve!
            val startRow = startRowQuery(authorityIdList, startTransactionId, order).firstOrNull()

            if (startRow == null)
                emptyList()
            else {
                transactionsByAuthoritiesQuery(authorityIdList, getStartValue(order, startRow), order)
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
        transaction(database) {
            SchemaUtils.drop(facts, transactions)
        }

        return ExposedEntityStore(database, signator, publicKeyProvider, eventBus)
    }

    private fun factFromResultRow(resultRow: ResultRow): Fact {
        return Fact(
            Id.decode(resultRow[facts.authorityId]),
            Id.decode(resultRow[facts.entityId]),
            CoreAttribute.values().single { a -> a.spec.uri.toString() == resultRow[facts.attribute] }.spec,
            Value(resultRow[facts.value].bytes),
            resultRow[facts.operation],
            resultRow[facts.epochSecond],
            resultRow[facts.transactionOrdinal]
        )
    }

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return transaction(database) {
            val facts = facts.select {
                (facts.authorityId eq Id.encode(authorityId) and (facts.entityId eq Id.encode(entityId)))
            }.map { factFromResultRow(it) }

            if (facts.isNotEmpty()) Entity.fromFacts(facts) else null
        }
    }

    override fun persistTransaction(signedTransaction: SignedTransaction): Long {
        return transaction(database) {
            val transaction = signedTransaction.transaction
            val transactionsId = transactions.insert {
                it[transactionId] = Id.encode(transaction.id)
                it[authorityId] = Id.encode(transaction.authorityId)
                it[epochSecond] = transaction.epochSecond
                it[encoded] = ExposedBlob(SignedTransaction.encode(signedTransaction))
            } get transactions.id

            val transactionFacts = transaction.getFacts(transactionsId.value)
            transactionFacts
                .forEach { fact ->
                facts.insert {
                    it[authorityId] = Id.encode(fact.authorityId)
                    it[entityId] = Id.encode(fact.entityId)
                    it[attribute] = fact.attribute.uri.toString()
                    it[value] = ExposedBlob(fact.value.bytes)
                    it[operation] = fact.operation
                    it[epochSecond] = transaction.epochSecond
                    it[transactionOrdinal] = transactionsId.value
                }
            }

            transactionsId.value
        }
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