/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.storage.entitystore

import io.opencola.event.bus.EventBus
import io.opencola.model.*
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import io.opencola.storage.entitystore.EntityStore.TransactionOrder.*
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.nio.file.Path

class ExposedEntityStore(
    name: String,
    // TODO DB should suffice. Can't remember why path was passed in.
    storagePath: Path,
    getDB: (Path) -> Database,
    signator: Signator,
    publicKeyProvider: PublicKeyProvider<Id>,
    eventBus: EventBus? = null,
) : AbstractEntityStore(signator, publicKeyProvider, eventBus, EncodingFormat.OC) {
    private val database: Database

    // NOTE: Some databases may truncate the table name. This is an issue to the degree that it increases the
    // chances of collisions. Given the number of ids stored in a single DB, the chances of issue are exceedingly low.
    // This would likely be an issue only when storing data for large sets of users (millions to billions?)
    // TODO: Magic numbers (32, 128) should come from config
    // TODO: Normalize attribute
    // TODO: Break out attribute into separate table
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
        database = getDB(storagePath.resolve("$name.db"))
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

    private fun Op<Boolean>.withIdConstraint(column: Column<ByteArray>, ids: Set<Id>): Op<Boolean> {
        return if (ids.isEmpty())
            this
        else
            this.and(ids
                .map { (column eq Id.encode(it)) }
                .reduce { acc, op -> acc.or(op) })

    }

    private fun getOrderColumn(order: TransactionOrder): Column<*> {
        return when (order) {
            IdAscending -> transactions.id
            IdDescending -> transactions.id
            TimeAscending -> transactions.epochSecond
            TimeDescending -> transactions.epochSecond
        }
    }

    private fun isAscending(order: TransactionOrder): Boolean {
        return when (order) {
            IdAscending -> true
            IdDescending -> false
            TimeAscending -> true
            TimeDescending -> false
        }
    }

    private fun transactionsByAuthoritiesQuery(authorityIds: Set<Id>, id: Long?, order: TransactionOrder): Query {
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
        authorityIds: Set<Id>,
        startTransactionId: Id?,
        order: TransactionOrder
    ): Query {
        return if (startTransactionId == null)
            transactionsByAuthoritiesQuery(authorityIds, null, order).limit(1)
        else
            transactions.select { transactions.transactionId eq Id.encode(startTransactionId) }
    }

    private fun getStartValue(order: TransactionOrder, row: ResultRow): Long {
        return when (order) {
            IdAscending -> row[transactions.id].value
            IdDescending -> row[transactions.id].value
            TimeAscending -> row[transactions.epochSecond]
            TimeDescending -> row[transactions.epochSecond]
        }
    }

    private fun getTransactionRows(
        authorityIds: Set<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): List<ResultRow> {
        return transaction(database) {
            // TODO: There's likely a seam problem lurking here when ordering by time,
            //  since time isn't unique. Solve!
            val startRow = startRowQuery(authorityIds, startTransactionId, order).firstOrNull()

            if (startRow == null)
                emptyList()
            else {
                transactionsByAuthoritiesQuery(authorityIds, getStartValue(order, startRow), order)
                    .limit(limit)
                    .toList()
            }

        }
    }

    override fun getSignedTransactions(
        authorityIds: Set<Id>,
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

    private fun factFromResultRow(resultRow: ResultRow): Fact? {
        val attribute = Attributes.getAttributeByUriString(resultRow[facts.attribute])

        if (attribute == null) {
            logger.warn { "Unknown attribute ${resultRow[facts.attribute]} - ignoring fact" }
            return null
        }

        return Fact(
            Id.decode(resultRow[facts.authorityId]),
            Id.decode(resultRow[facts.entityId]),
            attribute,
            attribute.valueWrapper.decodeAny(resultRow[facts.value].bytes),
            resultRow[facts.operation],
            resultRow[facts.epochSecond],
            resultRow[facts.transactionOrdinal]
        )
    }

    override fun persistTransaction(signedTransaction: SignedTransaction): Long {
        return transaction(database) {
            val transaction = signedTransaction.transaction
            val ordinal = transactions.insert {
                it[transactionId] = Id.encode(transaction.id)
                it[authorityId] = Id.encode(transaction.authorityId)
                it[epochSecond] = transaction.epochSecond
                it[encoded] = ExposedBlob(SignedTransaction.encode(signedTransaction))
            } get transactions.id

            val transactionFacts = transaction.getFacts(ordinal.value)
            transactionFacts
                .forEach { fact ->
                    facts.insert {
                        it[authorityId] = Id.encode(fact.authorityId)
                        it[entityId] = Id.encode(fact.entityId)
                        it[attribute] = fact.attribute.uri.toString()
                        it[value] = ExposedBlob(fact.attribute.valueWrapper.encodeAny(fact.value))
                        it[operation] = fact.operation
                        it[epochSecond] = transaction.epochSecond
                        it[transactionOrdinal] = ordinal.value
                    }
                }

            ordinal.value
        }
    }

    override fun getFacts(authorityIds: Set<Id>, entityIds: Set<Id>): List<Fact> {
        return transaction(database) {
            facts.select {
                (facts.id greaterEq 0)
                    .withIdConstraint(facts.authorityId, authorityIds)
                    .withIdConstraint(facts.entityId, entityIds)
            }.mapNotNull { factFromResultRow(it) }
        }
    }
}
