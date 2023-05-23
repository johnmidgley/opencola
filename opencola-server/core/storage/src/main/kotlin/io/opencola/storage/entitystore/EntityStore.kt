package io.opencola.storage.entitystore

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
    fun getFacts(authorityIds: Set<Id>, entityIds: Set<Id>) : List<Fact>

    fun updateEntities(vararg entities: Entity) : SignedTransaction?
    fun getEntities(authorityIds: Set<Id>, entityIds: Set<Id>) : List<Entity>
    fun deleteEntities(authorityId: Id, vararg entityIds: Id)

    // TODO: Should this be 'put' instead of 'add'?
    fun addSignedTransactions(signedTransactions: List<SignedTransaction>)
    fun getSignedTransactions(authorityIds: Set<Id>, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction>

    fun addSignedTransaction(signedTransaction: SignedTransaction) {
        addSignedTransactions(listOf(signedTransaction))
    }

    fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return getEntities(setOf(authorityId), setOf(entityId)).firstOrNull()
    }

    fun getLastTransactionId(authorityId: Id): Id? {
        return getSignedTransactions(setOf(authorityId), null, TransactionOrder.IdDescending, 1)
            .firstOrNull()
            .ifNotNullOrElse( { it.transaction.id }, { null } )
    }

    fun getTransaction(transactionId: Id): SignedTransaction? {
        return getSignedTransactions(setOf(), transactionId, TransactionOrder.IdAscending, 1).firstOrNull()
    }

    fun getSignedTransactions(authorityId: Id, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction> {
        return getSignedTransactions(setOf(authorityId), startTransactionId, order, limit)
    }

    fun getAllSignedTransactions(authorityIds: Set<Id> = emptySet(), batchSize: Int = 100): Sequence<SignedTransaction> {
        return sequence {
            var transactions =
                getSignedTransactions(authorityIds, null, TransactionOrder.IdAscending, batchSize)

            while (true) {
                transactions.forEach { yield(it) }
                if (transactions.count() < batchSize) {
                    break
                }
                transactions = getSignedTransactions(
                    authorityIds,
                    transactions.last().transaction.id,
                    TransactionOrder.IdAscending,
                    batchSize + 1
                ).drop(1)
            }
        }
    }
}

