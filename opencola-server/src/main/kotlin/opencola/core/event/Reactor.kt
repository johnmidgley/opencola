package opencola.core.event

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import opencola.core.event.EventBus.Event
import opencola.core.extensions.ifNotNullOrElse
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.network.PeerRouter.PeerStatus.Status.*
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.server.TransactionsResponse
import org.jetbrains.exposed.sql.not
import java.io.ByteArrayInputStream

interface Reactor {
    fun handleMessage(event: Event)
}

class MainReactor(
    private val authority: Authority,
    private val entityStore: EntityStore,
    private val searchIndex: SearchIndex,
    private val peerRouter: PeerRouter
) : Reactor {
    private val logger = KotlinLogging.logger("MainReactor")
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature){
            serializer = KotlinxSerializer()
        }
    }

    private fun handleNodeStarted(event: Event){
        logger.info { event.name }
        updatePeerTransactions()
    }

    private fun updatePeerTransactions() {
        logger.info { "Updating peer transactions" }
        // TODO: Swap runBlocking with adding to peerExecutor
        runBlocking {
            peerRouter.peers.forEach {
                async { requestTransactions(it) }
            }
        }
    }

    private fun getTransactionsUrl(peer: Peer, peerTransactionId: Id?): String {
        return "http://${peer.host}/transactions/${peer.id}${peerTransactionId.ifNotNullOrElse({"/${it.toString()}"}, {""})}?peerId=${authority.authorityId}"
    }

    private suspend fun requestTransactions(peer: Peer){
        // TODO: Update getTransaction to take authorityId
        logger.info { "Requesting transaction from: ${peer.name}" }

        try {
            var peerTransactionId = entityStore.getLastTransactionId(peer.id)

            // TODO - Config max batches
            // TODO: Set reasonable max batches and batch sizes
            for(batch in 1..10) {
                val urlString = getTransactionsUrl(peer, peerTransactionId)
                logger.info { "Requesting transactions - Batch $batch - $urlString" }

                //TODO - see implement PeerService.get(peer, path) to get rid of httpClient here
                // plus no need to update peer status here
                val transactionsResponse: TransactionsResponse =
                    httpClient.get(urlString)

                peerRouter.updateStatus(peer.id, Online)

                if(transactionsResponse.transactions.isEmpty())
                    break

                entityStore.addSignedTransactions(transactionsResponse.transactions)

                if(transactionsResponse.transactions.last().transaction.id == transactionsResponse.currentTransactionId)
                    break
            }
        }
        catch(e: java.net.ConnectException){
            logger.info { "${peer.name} appears to be offline." }
            peerRouter.updateStatus(peer.id, Offline)
        }
        catch (e: Exception){
            logger.error { e.message }
            // TODO: This should depend on the error
            peerRouter.updateStatus(peer.id, Offline)
        }
        logger.info { "Completed requesting transactions from: ${peer.name}" }
    }

    private fun requestTransactions(peerId: Id){
        //TODO: After requesting transactions, check to see if a new HEAD has been set (i.e. the transactions don't
        // chain to existing ones, which can happen if a peer deletes their store). If this happens, inform the user
        // and ask if "abandoned" transactions should be deleted.
        // TODO: Catch / handle this error and return appropriate forbidden / not authorized status
        val peer = peerRouter.getPeer(peerId)
            ?: throw IllegalArgumentException("Attempt to request transactions for unknown peer: $peerId ")


        // TODO: This blocks startup. Make fully async (and/or handle startup with event bus)
        // TODO: Remove all runBlocking - replace with appropriate executor
        runBlocking {
            async { requestTransactions(peer) }
        }
    }

    private fun handleNewTransaction(event: Event){
        logger.info { "Handling new transaction" }
        val signedTransaction = ByteArrayInputStream(event.data).use{ SignedTransaction.decode(it) }
        indexTransaction(signedTransaction)

        if (signedTransaction.transaction.authorityId == authority.authorityId) {
            // Transaction originated locally, so inform peers
            peerRouter.broadcastMessage(
                "notifications",
                PeerRouter.Notification(signedTransaction.transaction.authorityId, PeerRouter.Event.NewTransaction)
            )
        }
    }

    private fun indexTransaction(signedTransaction: SignedTransaction) {
        logger.info { "Indexing transaction: ${signedTransaction.transaction.id}" }
        val authorityId = signedTransaction.transaction.authorityId

        signedTransaction.transaction.transactionEntities
            .map { f -> f.entityId }
            .distinct()
            .forEach { eid ->
                val entity = entityStore.getEntity(authorityId, eid)

                if (entity == null)
                    logger.error { "Can't get entity after persisting entity facts: $authorityId:$eid" }
                else if (entity !is DataEntity){ // TODO: Should data entities be indexed?
                    //TODO: Archive will not be available - figure out what to do
                    // Call peer for data?
                    searchIndex.index(entity)
                }
            }
    }

    private fun handlePeerNotification(event: Event){
        logger.info { "Handling peer notification" }
        val notification = ByteArrayInputStream(event.data).use { PeerRouter.Notification.decode(it) }
        val previousStatus = peerRouter.updateStatus(notification.peerId, Online)

        when(notification.event){
            PeerRouter.Event.Online -> { if(previousStatus == Offline) requestTransactions(notification.peerId) }
            PeerRouter.Event.NewTransaction -> requestTransactions(notification.peerId)
        }
    }

    override fun handleMessage(event: Event) {
        logger.info { "Handling event: $event" }

        when(Events.valueOf(event.name)){
            Events.NodeStarted -> handleNodeStarted(event)
            Events.NewTransaction -> handleNewTransaction(event)
            Events.PeerNotification -> handlePeerNotification(event)
        }
    }
}