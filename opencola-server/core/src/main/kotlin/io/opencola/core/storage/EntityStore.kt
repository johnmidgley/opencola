package io.opencola.core.storage

import io.opencola.core.model.Entity
import io.opencola.core.model.Fact
import io.opencola.core.model.Id
import io.opencola.core.model.SignedTransaction
import io.opencola.core.extensions.ifNotNullOrElse

interface EntityStore {
    enum class TransactionOrder {
        IdAscending,
        IdDescending,
        TimeAscending,
        TimeDescending,
    }

    // TODO: Separate fact store from entity store?
    fun getFacts(authorityIds: Iterable<Id>, entityIds: Iterable<Id>) : List<Fact>

    // TODO: Replace getEntity with a call to getEntities
    fun getEntity(authorityId: Id, entityId: Id): Entity?
    fun getEntities(authorityIds: Set<Id>, entityIds: Set<Id>) : List<Entity>
    fun deleteEntity(authorityId: Id, entityId: Id)
    fun updateEntities(vararg entities: Entity) : SignedTransaction?
    fun addSignedTransactions(signedTransactions: List<SignedTransaction>)
    fun getSignedTransactions(authorityIds: Iterable<Id>, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction>

    // SHOULD ONLY BE USED FOR TESTING OR IF YOU REALLY MEAN IT
    fun resetStore() : EntityStore

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
}

