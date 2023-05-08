package io.opencola.storage

import io.opencola.model.Entity
import io.opencola.model.Fact
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.util.ifNotNullOrElse

interface EntityStore {
    enum class TransactionOrder {
        IdAscending,
        IdDescending,
        TimeAscending,
        TimeDescending,
    }

    // TODO: Return Sequences instead of a Lists

    // TODO: Separate fact store from entity store?
    fun getFacts(authorityIds: Iterable<Id>, entityIds: Iterable<Id>) : List<Fact>

    fun getEntities(authorityIds: Set<Id>, entityIds: Set<Id>) : List<Entity>
    fun deleteEntities(authorityId: Id, vararg entityIds: Id)
    fun updateEntities(vararg entities: Entity) : SignedTransaction?
    // TODO: Should this be 'put' instead of 'add'?
    fun addSignedTransactions(signedTransactions: List<SignedTransaction>)
    fun getSignedTransactions(authorityIds: Iterable<Id>, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction>

    fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return getEntities(setOf(authorityId), setOf(entityId)).firstOrNull()
    }

    fun getLastTransactionId(authorityId: Id): Id? {
        return getSignedTransactions(listOf(authorityId), null, TransactionOrder.IdDescending, 1)
            .firstOrNull()
            .ifNotNullOrElse( { it.transaction.id }, { null } )
    }

    fun getTransaction(transactionId: Id): SignedTransaction? {
        return getSignedTransactions(listOf(), transactionId, TransactionOrder.IdAscending, 1).firstOrNull()
    }

    fun getSignedTransactions(authorityId: Id, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction> {
        return getSignedTransactions(listOf(authorityId), startTransactionId, order, limit)
    }

    fun getAllTransactions(authorityIds: Iterable<Id> = emptyList(), batchSize: Int = 100): Sequence<SignedTransaction> {
        return sequence {
            var transactions =
                getSignedTransactions(authorityIds, null, TransactionOrder.IdAscending, batchSize)

            while (true) {
                transactions.forEach { yield(it) }
                if (transactions.count() < batchSize) {
                    break
                }
                transactions = getSignedTransactions(
                    emptyList(),
                    transactions.last().transaction.id,
                    TransactionOrder.IdAscending,
                    batchSize + 1
                ).drop(1)
            }
        }
    }
}


