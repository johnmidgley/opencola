package io.opencola.core.storage

import io.opencola.core.event.EventBus
import io.opencola.core.model.*
import io.opencola.security.Signator
import io.opencola.core.storage.EntityStore.TransactionOrder
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SimpleEntityStore(
    val path: Path,
    authority: Authority,
    signator: Signator,
    addressBook: AddressBook?,
    eventBus: EventBus?,
    ) : AbstractEntityStore(authority, signator, addressBook, eventBus) {
    // TODO: Synchronize access
    private var transactions =
        if (!path.exists()) {
            logger.warn { "No entity store found at $path. Will get created on update" }
            emptyList()
        } else {
            transactionsFromPath(path).toList()
        }

    private var nextTransactionOrdinal = 0L

    private var facts =
        transactions
            .filter { isValidTransaction(it) }
            .flatMap { it.transaction.getFacts(nextTransactionOrdinal++) }
            .toList()


    private fun transactionsFromPath(path: Path): Sequence<SignedTransaction> {
        return sequence {
            path.inputStream().use {
                // TODO: Make sure available works properly. From the docs, it seems like it can return 0 when no buffered data left.
                // Can't find the idiomatic way to check for end of file. May need to read until exception??
                while (it.available() > 0)
                    yield(SignedTransaction.decode(it))

                if (it.read() != -1) {
                    logAndThrow(RuntimeException("While reading transactions, encountered available() == 0 but read() != -1"))
                }
            }
        }
    }

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        // TODO: Entity.fromFacts
        return Entity.fromFacts(facts.filter { it.authorityId == authorityId && it.entityId == entityId }.toList())
    }

    private fun orderedTransactions(order: TransactionOrder): List<SignedTransaction> {
        return when(order){
            TransactionOrder.IdAscending -> transactions
            TransactionOrder.IdDescending -> transactions.reversed()
            TransactionOrder.TimeAscending -> transactions.sortedBy { it.transaction.epochSecond }
            TransactionOrder.TimeDescending -> transactions.sortedByDescending { it.transaction.epochSecond }
        }
    }

    override fun getSignedTransactions(
        authorityIds: Iterable<Id>,
        startTransactionId: Id?,
        order: TransactionOrder,
        limit: Int
    ): Iterable<SignedTransaction> {
        val authorityIdList = authorityIds.toList()

        return orderedTransactions(order)
            .dropWhile { startTransactionId != null && it.transaction.id != startTransactionId }
            .filter { authorityIdList.isEmpty() || authorityIdList.contains(it.transaction.authorityId) }
            .take(limit)
    }

    @Synchronized
    override fun persistTransaction(signedTransaction: SignedTransaction): Long {
        if (transactions.any { it.transaction.id == signedTransaction.transaction.id })
            throw IllegalArgumentException("Attempt to insert duplicate transaction: ${signedTransaction.transaction.id}")

        path.outputStream(StandardOpenOption.APPEND, StandardOpenOption.CREATE)
            .use { SignedTransaction.encode(it, signedTransaction) }
        transactions = transactions + signedTransaction

        val transactionOrdinal = nextTransactionOrdinal++
        val transactionFacts = signedTransaction.transaction.getFacts(transactionOrdinal)
        facts = facts + transactionFacts

        return transactionOrdinal
    }

    override fun getFacts(authorityIds: Iterable<Id>, entityIds: Iterable<Id>): List<Fact> {
        val authorityIdList = authorityIds.toList()
        val entityIdList = entityIds.toList()

        return facts.filter {
            (authorityIdList.isEmpty() || authorityIdList.contains(it.authorityId)
                    && (entityIdList.isEmpty() || entityIdList.contains(it.entityId)))
        }
    }

    override fun resetStore(): SimpleEntityStore {
        path.deleteIfExists()
        return SimpleEntityStore(path, authority, signator, addressBook, eventBus)
    }
}