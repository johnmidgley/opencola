package opencola.core.storage

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import opencola.core.config.Application
import opencola.core.model.Entity
import opencola.core.model.Id
import opencola.core.model.SignedTransaction
import opencola.core.network.Peer
import opencola.server.TransactionsHandler.*
import opencola.service.PeerService
import opencola.service.PeerService.*


class NetworkedEntityStore(private val entityStore: EntityStore, private val peerService: PeerService) : EntityStore {
    val logger = Application.instance.logger
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    init{

    }

    private suspend fun requestTransactions(peer: Peer){
        // TODO: Update getTransaction to take authorityId
        var currentTransactionId = entityStore.getTransactionId(peer.id)

        try {
            // TODO - Config match batches
            for(batch in 1..10) {
                val urlString = "http://${peer.host}/transactions/${peer.id}/$currentTransactionId"
                logger.info { "Requesting transactions - Batch $batch - $urlString" }

                val transactionsResponse: TransactionsResponse =
                    httpClient.get(urlString)


                // peer.status = Peer.Status.Online

                transactionsResponse.transactions.forEach {
                    entityStore.persistTransaction(it)
                    // TODO: Search Indexing should happen here!
                }

                currentTransactionId = transactionsResponse.transactions.maxOf { it.transaction.id }

                if(currentTransactionId == transactionsResponse.currentTransactionId)
                    break
            }
        } catch (e: Exception){
            logger.error { e.message }
            // TODO: This should depend on the error
            // peer.status = Peer.Status.Offline
        }
    }

    override fun getEntity(authorityId: Id, entityId: Id): Entity? {
        return entityStore.getEntity(authorityId, entityId)
    }

    override fun commitChanges(vararg entities: Entity): SignedTransaction? {
        val signedTransaction = entityStore.commitChanges(*entities)

        if(signedTransaction != null){
            peerService.broadcastMessage("notifications", Notification(signedTransaction.transaction.authorityId, Event.NewTransactions))
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