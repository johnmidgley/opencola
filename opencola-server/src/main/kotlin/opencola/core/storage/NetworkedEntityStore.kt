package opencola.core.storage

import opencola.core.config.Application
import opencola.core.config.Network
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction

class NetworkedEntityStore(private val entityStore: EntityStore, private val networkConfig: Network) : EntityStore {
    val logger = Application.instance.logger

    init{
        // TODO: Request any new transactions from peers (in the background)
    }

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return entityStore.getEntity(authorityId, entityId)
    }

    override fun commitChanges(vararg entities: Entity): SignedTransaction? {
        val signedTransaction = entityStore.commitChanges(*entities)

        if(signedTransaction != null) {
            networkConfig.peers.forEach {
                logger.info { "Sending transaction {${signedTransaction.transaction.id}} to ${it.name}@${it.ip}" }
                logger.error { "Implement network call" }
            }
        }

        return signedTransaction
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        entityStore.persistTransaction(signedTransaction)
    }

    override fun getTransaction(authorityId: Id, transactionId: Long): SignedTransaction? {
        return entityStore.getTransaction(authorityId, transactionId)
    }

    override fun getTransactions(
        authorityId: Id,
        startTransactionId: Long,
        endTransactionId: Long
    ): Iterable<SignedTransaction> {
        return entityStore.getTransactions(authorityId, startTransactionId, endTransactionId)
    }

    override fun getTransactionId(): Long {
        return entityStore.getTransactionId()
    }

    override fun resetStore(): EntityStore {
        return entityStore.resetStore()
    }
}