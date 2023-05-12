package io.opencola.storage.entitystore

import io.opencola.event.EventBus
import io.opencola.model.*
import io.opencola.model.Attributes as ModelAttributes
import io.opencola.security.PublicKeyProvider
import io.opencola.security.Signator
import io.opencola.serialization.EncodingFormat
import io.opencola.storage.entitystore.EntityStore.TransactionOrder
import io.opencola.storage.entitystore.EntityStore.TransactionOrder.*
import io.opencola.storage.filestore.IdBasedFileStore
import io.opencola.storage.filestore.LocalIdBasedFileStore
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath

class ExposedEntityStoreV2(
    name: String,
    config: EntityStoreConfig,
    storagePath: Path,
    getDB: (Path) -> Database,
    modelAttributes: Iterable<Attribute>,
    signator: Signator,
    publicKeyProvider: PublicKeyProvider<Id>,
    eventBus: EventBus? = null,
) : AbstractEntityStore(config, signator, publicKeyProvider, eventBus, EncodingFormat.PROTOBUF) {
    private val database: Database
    private val transactionStoragePath: Path
    private val transactionFileStore: IdBasedFileStore

    // TODO: Normalize attribute
    // TODO: Break out attribute into separate table
    private class Attributes(name: String = "Attributes") : LongIdTable(name) {
        val name = text("name")
        val type = enumeration("type", AttributeType::class)
        val uri = text("uri").uniqueIndex()
    }

    private class Facts(name: String = "Facts") : LongIdTable(name) {
        val authorityId = binary("authorityId", Id.lengthInBytes).index()
        val entityId = binary("entityId", Id.lengthInBytes).index()
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
        // val encoded = blob("encoded")
    }

    private val attributeUriToDbIdMap: Map<URI, Long>
    private val attributes: Attributes
    private val facts: Facts
    private val transactions: Transactions

    init {
        require(config.transactionStorageUri == null || config.transactionStorageUri.scheme == "file") { "Unsupported scheme: ${config.transactionStorageUri?.scheme}" }

        storagePath.toFile().mkdirs()
        database = getDB(storagePath.resolve("${name}V2.db"))
        transactionStoragePath = config.transactionStorageUri?.toPath()
            ?: storagePath.resolve("transactions").also { it.toFile().mkdirs() }
        transactionFileStore = LocalIdBasedFileStore(transactionStoragePath)

        logger.info { "Initializing ExposedEntityStoreV2 {${database.url}}" }

        this.attributes = Attributes()
        this.facts = Facts()
        this.transactions = Transactions()

        initTables()
        attributeUriToDbIdMap = initDbAttributes(modelAttributes)
    }

    private fun initTables() {
        transaction(database) {
            SchemaUtils.create(attributes)
            SchemaUtils.create(facts)
            SchemaUtils.create(transactions)
        }
    }

    private fun addMissingAttributes(modelAttributes: Iterable<Attribute>) {
        val dbAttributeUris = transaction(database) {
            attributes.selectAll().map { it[attributes.uri] }
        }

        val missingAttributes = modelAttributes.filter { !dbAttributeUris.contains(it.uri.toString()) }
        if (missingAttributes.isNotEmpty()) {
            transaction(database) {
                attributes.batchInsert(missingAttributes) {
                    this[attributes.name] = it.name
                    this[attributes.type] = it.type
                    this[attributes.uri] = it.uri.toString()
                }
            }
        }
    }

    private fun getAttributeUriToDbIdMap(): Map<URI, Long> {
        return transaction(database) {
            attributes.selectAll().associate { URI(it[attributes.uri]) to it[attributes.id].value }
        }
    }

    private fun initDbAttributes(modelAttributes: Iterable<Attribute>): Map<URI, Long> {
        addMissingAttributes(modelAttributes)
        return getAttributeUriToDbIdMap()
    }


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

    override fun persistTransaction(signedTransaction: SignedTransaction): Long {
        transactionFileStore.write(signedTransaction.transaction.id, signedTransaction.encodeProto())

        return transaction(database) {
            val transaction = signedTransaction.transaction
            val ordinal = transactions.insert {
                it[transactionId] = Id.encode(transaction.id)
                it[authorityId] = Id.encode(transaction.authorityId)
                it[epochSecond] = transaction.epochSecond
                // it[encoded] = ExposedBlob(signedTransaction.toBytes())
            } get transactions.id

            val transactionFacts = transaction.getFacts(ordinal.value)
            transactionFacts
                .forEach { fact ->
                    facts.insert {
                        it[authorityId] = Id.encode(fact.authorityId)
                        it[entityId] = Id.encode(fact.entityId)
                        it[attribute] = fact.attribute.uri.toString()
                        it[value] = ExposedBlob(fact.attribute.valueWrapper.encodeProto(fact.value))
                        it[operation] = fact.operation
                        it[epochSecond] = transaction.epochSecond
                        it[transactionOrdinal] = ordinal.value
                    }
                }

            ordinal.value
        }
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

    private fun transactionsByAuthoritiesQuery(authorityIds: List<Id>, id: Long?, order: TransactionOrder): Query {
        val orderColumn = getOrderColumn(order)
        val isAscending = isAscending(order)

        return transactions
            .select {
                (orderColumn greaterEq 0) // Not elegant, but avoids separate selectAll clause when no constraints provided
                    .withLongColumnOrdering(orderColumn, id, isAscending)
                    .withIdConstraint(transactions.authorityId, authorityIds)
            }
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
        return when (order) {
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
            val startRow = startRowQuery(authorityIds.toList(), startTransactionId, order).firstOrNull()

            startRow?.let {
                transactionsByAuthoritiesQuery(authorityIdList, getStartValue(order, startRow), order)
                    .limit(limit)
                    .toList()
            } ?: emptyList()
        }
    }

    override fun getSignedTransactions(
        authorityIds: Iterable<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): Iterable<SignedTransaction> {
        return getTransactionRows(authorityIds, startTransactionId, order, limit).map { row ->
            // SignedTransaction.fromBytes(row[transactions.encoded].bytes)
            transactionFileStore.read(Id.decode(row[transactions.transactionId]))?.let {
                SignedTransaction.decodeProto(it)
            } ?: error("Transaction not found")
        }
    }

    private fun factFromResultRow(resultRow: ResultRow): Fact {
        val attribute = ModelAttributes.getAttributeByUriString(resultRow[facts.attribute])!!

        return Fact(
            Id.decode(resultRow[facts.authorityId]),
            Id.decode(resultRow[facts.entityId]),
            attribute,
            attribute.valueWrapper.decodeProto(resultRow[facts.value].bytes),
            resultRow[facts.operation],
            resultRow[facts.epochSecond],
            resultRow[facts.transactionOrdinal]
        )
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
