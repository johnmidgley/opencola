package opencola.core.storage

import opencola.core.extensions.ifNotNullOrElse
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction

interface EntityStore {
    enum class TransactionOrder {
        Ascending,
        Descending,
    }

    fun getEntity(authorityId: Id, entityId: Id): Entity?
    fun updateEntities(vararg entities: Entity) : SignedTransaction?
    fun addTransactions(signedTransactions: List<SignedTransaction>)
    fun getTransactions(authorityIds: Iterable<Id>, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction>

    // SHOULD ONLY BE USED FOR TESTING OR IF YOU REALLY MEAN IT
    fun resetStore() : EntityStore

    fun getLastTransactionId(authorityId: Id): Id? {
        return getTransactions(listOf(authorityId), null, TransactionOrder.Descending, 1)
            .firstOrNull()
            .ifNotNullOrElse( { it.transaction.id }, { null } )
    }

    fun getTransaction(transactionId: Id): SignedTransaction? {
        return getTransactions(listOf(), transactionId, TransactionOrder.Ascending, 1).firstOrNull()
    }

    fun getTransactions(authorityId: Id, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction> {
        return getTransactions(listOf(authorityId), startTransactionId, order, limit)
    }
}

