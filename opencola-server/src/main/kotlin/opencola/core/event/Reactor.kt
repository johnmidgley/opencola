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
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.network.PeerRouter.PeerStatus.Status.*
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import java.io.ByteArrayInputStream

interface Reactor {
    fun handleMessage(event: Event)
}

// TODO: Invert this by having the reactor subscribe to events vs. being plugged in to the event bus
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
        if(peerRouter.peers.isNotEmpty())
            logger.info { "Updating peer transactions" }

        // TODO: Swap runBlocking with adding to peerExecutor
        runBlocking {
            peerRouter.peers
                .filter { it.active }
                .forEach {
                async { requestTransactions(it) }
            }
        }
    }

    private suspend fun requestTransactions(peer: Peer){
        try {
            var peerTransactionId = entityStore.getLastTransactionId(peer.id)

            // TODO - Config max batches
            // TODO: Set reasonable max batches and batch sizes
            for(batch in 1..10) {
                logger.info { "Requesting transactions from ${peer.name} - Batch $batch" }

                //TODO - see implement PeerService.get(peer, path) to get rid of httpClient here
                // plus no need to update peer status here
                val transactionsResponse = peerRouter.getTransactions(authority, peer, peerTransactionId)

                if(transactionsResponse == null || transactionsResponse.transactions.isEmpty())
                    break

                entityStore.addSignedTransactions(transactionsResponse.transactions)
                peerTransactionId = transactionsResponse.transactions.last().transaction.id

                if(peerTransactionId == transactionsResponse.currentTransactionId)
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
            .forEach { entityId ->
                val entity = entityStore.getEntity(authorityId, entityId)

                if (entity == null) {
                    // Entity was deleted
                    searchIndex.delete(authorityId, entityId)
                }
                else if (entity !is DataEntity){ // TODO: Should data entities be indexed?
                    //TODO: Archive will not be available - figure out what to do
                    // Call peer for data?
                    searchIndex.add(entity)
                }
            }
    }

    private fun handlePeerNotification(event: Event){
        val notification = ByteArrayInputStream(event.data).use { PeerRouter.Notification.decode(it) }
        logger.info { "Handling notification for peer ${notification.peerId} event: ${notification.event}" }

        when(notification.event){
            PeerRouter.Event.Online -> { requestTransactions(notification.peerId) }
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