package opencola.core.storage

import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction

interface EntityStore {
    fun getEntity(authorityId: Id, entityId: Id): Entity?
    fun updateEntities(vararg entities: Entity) : SignedTransaction?
    fun addTransactions(signedTransactions: List<SignedTransaction>)
    fun getTransactions(authorityId: Id, startTransactionId: Id?, numTransactions: Int) : Iterable<SignedTransaction>
    fun getLastTransactionId(authorityId: Id): Id?

    // SHOULD ONLY BE USED FOR TESTING OR IF YOU REALLY MEAN IT
    fun resetStore() : EntityStore
}