package opencola.core.storage

import opencola.core.extensions.ifNotNullOrElse
import opencola.core.model.Entity
import opencola.core.model.Fact
import opencola.core.model.Id
import opencola.core.model.SignedTransaction

interface EntityStore {
    enum class TransactionOrder {
        Ascending,
        Descending,
    }

    // TODO: Separate fact store from entity store?
    fun getFacts(authorityIds: Iterable<Id>, entityIds: Iterable<Id>) : List<Fact>

    fun getEntity(authorityId: Id, entityId: Id): Entity?
    fun updateEntities(vararg entities: Entity) : SignedTransaction?
    fun addSignedTransactions(signedTransactions: List<SignedTransaction>)
    fun getSignedTransactions(authorityIds: Iterable<Id>, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction>

    // SHOULD ONLY BE USED FOR TESTING OR IF YOU REALLY MEAN IT
    fun resetStore() : EntityStore

    fun getLastTransactionId(authorityId: Id): Id? {
        return getSignedTransactions(listOf(authorityId), null, TransactionOrder.Descending, 1)
            .firstOrNull()
            .ifNotNullOrElse( { it.transaction.id }, { null } )
    }

    fun getTransaction(transactionId: Id): SignedTransaction? {
        return getSignedTransactions(listOf(), transactionId, TransactionOrder.Ascending, 1).firstOrNull()
    }

    fun getSignedTransactions(authorityId: Id, startTransactionId: Id?, order: TransactionOrder, limit: Int) : Iterable<SignedTransaction> {
        return getSignedTransactions(listOf(authorityId), startTransactionId, order, limit)
    }
}

