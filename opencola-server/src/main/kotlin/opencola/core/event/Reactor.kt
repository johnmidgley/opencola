package opencola.core.event

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import opencola.core.event.EventBus.Event
import opencola.core.model.*
import opencola.core.network.*
import opencola.core.network.PeerEvent.*
import opencola.core.search.SearchIndex
import opencola.core.storage.AddressBook
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
    private val networkNode: NetworkNode,
    private val addressBook: AddressBook,
) : Reactor {
    private val logger = KotlinLogging.logger("MainReactor")

    private fun handleNodeStarted(event: Event){
        logger.info { event.name }
        updatePeerTransactions()
    }

    private fun updatePeerTransactions() {
        val peers = addressBook.getAuthorities(filterActive = true)

        if(peers.isNotEmpty()) {
            logger.info { "Updating peer transactions" }

            // TODO: Swap runBlocking with adding to peerExecutor
            runBlocking {
                peers
                    .forEach {
                        if(it.entityId != authority.entityId)
                            async { requestTransactions(it.entityId) }
                    }
            }
        }
    }

    private fun requestTransactions(peer: Authority) {
        var mostRecentTransactionId = entityStore.getLastTransactionId(peer.entityId)

        // TODO - Config max batches
        // TODO: Set reasonable max batches and batch sizes
        for (batch in 1..10) {
            logger.info { "Requesting transactions from ${peer.name} - Batch $batch" }
            val baseParams = mapOf("authorityId" to peer.entityId.toString())
            val params = if(mostRecentTransactionId == null)
                baseParams
            else
                baseParams.plus(Pair("mostRecentTransactionId", mostRecentTransactionId.toString()))

            val request = Request(authority.entityId, Request.Method.GET, "/transactions", null, params)
            val transactionsResponse = networkNode.sendRequest(peer.entityId, request)?.decodeBody<TransactionsResponse>()

            if (transactionsResponse == null || transactionsResponse.transactions.isEmpty())
                break

            entityStore.addSignedTransactions(transactionsResponse.transactions)
            mostRecentTransactionId = transactionsResponse.transactions.last().transaction.id

            if (mostRecentTransactionId == transactionsResponse.currentTransactionId)
                break
        }

        logger.info { "Completed requesting transactions from: ${peer.name}" }
    }

    private fun requestTransactions(peerId: Id){
        //TODO: After requesting transactions, check to see if a new HEAD has been set (i.e. the transactions don't
        // chain to existing ones, which can happen if a peer deletes their store). If this happens, inform the user
        // and ask if "abandoned" transactions should be deleted.
        // TODO: Catch / handle this error and return appropriate forbidden / not authorized status
        val peer = addressBook.getAuthority(peerId)
            ?: throw IllegalArgumentException("Attempt to request transactions for unknown peer: $peerId ")

        // TODO: This blocks startup. Make fully async (and/or handle startup with event bus)
        // TODO: Remove all runBlocking - replace with appropriate executor
         requestTransactions(peer)
    }

    private fun handleNewTransaction(event: Event){
        logger.info { "Handling new transaction" }
        val signedTransaction = ByteArrayInputStream(event.data).use{ SignedTransaction.decode(it) }
        indexTransaction(signedTransaction)

        if (signedTransaction.transaction.authorityId == authority.authorityId) {
            // Transaction originated locally, so inform peers

            val request = request(
                authority.authorityId,
                Request.Method.POST,
                "/notifications",
                null,
                null,
                Notification(signedTransaction.transaction.authorityId, NewTransaction)
            )

            networkNode.broadcastRequest(request)
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
        val notification = ByteArrayInputStream(event.data).use { Notification.decode(it) }
        logger.info { "Handling notification for peer ${notification.peerId} event: ${notification.event}" }

        when(notification.event){
            Added -> requestTransactions(notification.peerId)
            Online -> requestTransactions(notification.peerId)
            NewTransaction -> requestTransactions(notification.peerId)
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