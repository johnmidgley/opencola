package opencola.core.storage

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import opencola.core.config.Application
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.service.PeerService


class NetworkedEntityStore(private val entityStore: EntityStore, private val peerService: PeerService) : EntityStore {
    val logger = Application.instance.logger

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return entityStore.getEntity(authorityId, entityId)
    }

    override fun commitChanges(vararg entities: Entity): SignedTransaction? {
        val signedTransaction = entityStore.commitChanges(*entities)

        if(signedTransaction != null){
            peerService.broadcastTransaction(signedTransaction)
        }

        return signedTransaction
    }

    override fun persistTransaction(signedTransaction: SignedTransaction) {
        // TODO: Is there a case where persisted transactions should be broadcast too?
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

    override fun getTransactionId(authorityId: Id): Long {
        return entityStore.getTransactionId(authorityId)
    }

    override fun resetStore(): EntityStore {
        return entityStore.resetStore()
    }
}