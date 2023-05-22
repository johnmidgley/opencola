package io.opencola.storage.entitystore

import io.opencola.event.EventBus
import io.opencola.model.*
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
    private object Attributes : LongIdTable("Attributes") {
        val name = text("name")
        val type = enumeration("type", AttributeType::class)
        val uri = text("uri").uniqueIndex()
    }

    private object Facts : LongIdTable("Facts") {
        val authorityId = binary("authorityId", Id.lengthInBytes).index()
        val entityId = binary("entityId", Id.lengthInBytes).index()
        val attribute = long("attribute").references(Attributes.id)
        val value = blob("value")
        val operation = enumeration("operation", Operation::class)
        val epochSecond = long("epochSecond")
        val transactionOrdinal = long("transactionOrdinal")
    }

    // LongIdTable has implicit, autoincrement long id field
    private object Transactions : LongIdTable("Transactions") {
        val transactionId = binary("transactionId", 32).uniqueIndex()
        val authorityId = binary("authorityId", 32)
        val epochSecond = long("epochSecond").index()
        // val encoded = blob("encoded")
    }

    private val attributeUriToDbIdMap: Map<URI, Long>
    private val attributeDbIdToModelAttributeMap: Map<Long, Attribute>

    init {
        require(config.transactionStorageUri == null || config.transactionStorageUri.scheme == "file") { "Unsupported scheme: ${config.transactionStorageUri?.scheme}" }

        storagePath.toFile().mkdirs()
        database = getDB(storagePath.resolve("${name}.v2.db"))
        logger.info { "Initializing ExposedEntityStoreV2 {${database.url}}" }
        transactionStoragePath = config.transactionStorageUri?.toPath()
            ?: storagePath.resolve("transactions").also { it.toFile().mkdirs() }
        transactionFileStore = LocalIdBasedFileStore(transactionStoragePath)
        initTables()
        attributeUriToDbIdMap = initDbAttributes(modelAttributes)
        val uriToAttributeMap = modelAttributes.associateBy { it.uri }
        attributeDbIdToModelAttributeMap =
            attributeUriToDbIdMap.entries.associate { it.value to uriToAttributeMap[it.key]!! }

    }

    private fun initTables() {
        transaction(database) {
            SchemaUtils.create(Attributes)
            SchemaUtils.create(Facts)
            SchemaUtils.create(Transactions)
        }
    }

    private fun addMissingAttributes(modelAttributes: Iterable<Attribute>) {
        val dbAttributeUris = transaction(database) {
            Attributes.selectAll().map { it[Attributes.uri] }
        }

        val missingAttributes = modelAttributes.filter { !dbAttributeUris.contains(it.uri.toString()) }
        if (missingAttributes.isNotEmpty()) {
            transaction(database) {
                Attributes.batchInsert(missingAttributes) {
                    this[Attributes.name] = it.name
                    this[Attributes.type] = it.type
                    this[Attributes.uri] = it.uri.toString()
                }
            }
        }
    }

    private fun getAttributeUriToDbIdMap(): Map<URI, Long> {
        return transaction(database) {
            Attributes.selectAll().associate { URI(it[Attributes.uri]) to it[Attributes.id].value }
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

    private fun addTransactionToDB(signedTransaction: SignedTransaction): Long {
        return transaction(database) {
            val transaction = signedTransaction.transaction
            val ordinal = Transactions.insert {
                it[transactionId] = Id.encode(transaction.id)
                it[authorityId] = Id.encode(transaction.authorityId)
                it[epochSecond] = transaction.epochSecond
                // it[encoded] = ExposedBlob(signedTransaction.toBytes())
            } get Transactions.id

            val transactionFacts = transaction.getFacts(ordinal.value)
            transactionFacts
                .forEach { fact ->
                    if (fact.operation == Operation.Add && fact.value == io.opencola.model.value.emptyValue) {
                        throw IllegalArgumentException("Attempt to add empty value for attribute ${fact.attribute}")
                    }

                    Facts.insert {
                        it[authorityId] = Id.encode(fact.authorityId)
                        it[entityId] = Id.encode(fact.entityId)
                        it[attribute] = attributeUriToDbIdMap[fact.attribute.uri]!!
                        it[value] = ExposedBlob(fact.attribute.valueWrapper.encodeProto(fact.value))
                        it[operation] = fact.operation
                        it[epochSecond] = transaction.epochSecond
                        it[transactionOrdinal] = ordinal.value
                    }
                }

            ordinal.value
        }
    }


    override fun persistTransaction(signedTransaction: SignedTransaction): Long {
        if (!transactionFileStore.exists(signedTransaction.transaction.id)) {
            // Local transactions get added to the filestore here. Foreign transactions are added in addSignedTransactions
            transactionFileStore.write(signedTransaction.transaction.id, signedTransaction.encodeProto())
        }

        val ordinal = addTransactionToDB(signedTransaction)

        try {
           // Added any cached transactions (those received out of order) that were waiting for this transaction
            while (true) {
                val nextTransactionId = getNextTransactionId(signedTransaction.transaction.authorityId)

                transactionFileStore.read(nextTransactionId)?.let {
                    logger.info { "Adding cached transaction: $nextTransactionId" }
                    addTransactionToDB(SignedTransaction.decodeProto(it))
                } ?: break
            }
        } catch (e: Exception) {
            logger.error(e) { "Error adding cached transaction to DB: $e" }
        }

        return ordinal
    }

    private fun getOrderColumn(order: TransactionOrder): Column<*> {
        return when (order) {
            IdAscending -> Transactions.id
            IdDescending -> Transactions.id
            TimeAscending -> Transactions.epochSecond
            TimeDescending -> Transactions.epochSecond
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

        return Transactions
            .select {
                (orderColumn greaterEq 0) // Not elegant, but avoids separate selectAll clause when no constraints provided
                    .withLongColumnOrdering(orderColumn, id, isAscending)
                    .withIdConstraint(Transactions.authorityId, authorityIds)
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
            Transactions.select { Transactions.transactionId eq Id.encode(startTransactionId) }
    }

    private fun getStartValue(order: TransactionOrder, row: ResultRow): Long {
        return when (order) {
            IdAscending -> row[Transactions.id].value
            IdDescending -> row[Transactions.id].value
            TimeAscending -> row[Transactions.epochSecond]
            TimeDescending -> row[Transactions.epochSecond]
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
        authorityIds: Set<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): Iterable<SignedTransaction> {
        return getTransactionRows(authorityIds, startTransactionId, order, limit).map { row ->
            // SignedTransaction.fromBytes(row[Transactions.encoded].bytes)
            transactionFileStore.read(Id.decode(row[Transactions.transactionId]))?.let {
                SignedTransaction.decodeProto(it)
            } ?: error("Transaction not found")
        }
    }

    private fun factFromResultRow(resultRow: ResultRow): Fact? {
        val attribute = attributeDbIdToModelAttributeMap[resultRow[Facts.attribute]]

        if (attribute == null) {
            logger.warn { "Unknown attribute ${resultRow[Facts.attribute]} - ignoring fact" }
            return null
        }

        return Fact(
            Id.decode(resultRow[Facts.authorityId]),
            Id.decode(resultRow[Facts.entityId]),
            attribute,
            attribute.valueWrapper.decodeProto(resultRow[Facts.value].bytes),
            resultRow[Facts.operation],
            resultRow[Facts.epochSecond],
            resultRow[Facts.transactionOrdinal]
        )
    }

    override fun getFacts(authorityIds: Set<Id>, entityIds: Set<Id>): List<Fact> {
        return transaction(database) {
            Facts.select {
                (Facts.id greaterEq 0)
                    .withIdConstraint(Facts.authorityId, authorityIds.toList())
                    .withIdConstraint(Facts.entityId, entityIds.toList())
            }.mapNotNull { factFromResultRow(it) }
        }
    }

    override fun addSignedTransactions(signedTransactions: List<SignedTransaction>) {
        // We process transactions here, before calling super.addSignedTransactions, so that we can store out
        // of order transactions for future use, so they don't need to be requested again.
        signedTransactions.forEach {
            if (publicKeyProvider.getPublicKey(it.transaction.authorityId) != null && !transactionFileStore.exists(it.transaction.id)) {
                // This is a legitimate transaction from a known authority, so store it, even if it's out of order,
                // so we can use it later
                transactionFileStore.write(it.transaction.id, it.encodeProto())
            }
        }

        super.addSignedTransactions(signedTransactions)
    }

    fun getNextTransactionIdForV2MigrationOnly(authorityId: Id): Id {
        return super.getNextTransactionId(authorityId)
    }
}
