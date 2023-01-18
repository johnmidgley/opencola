package io.opencola.core.config

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.event.Reactor
import io.opencola.core.network.*
import io.opencola.storage.AddressBook
import io.opencola.storage.EntityStore
import io.opencola.model.Authority
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.search.SearchIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.ByteArrayInputStream

// TODO: Invert this by having the reactor subscribe to events vs. being plugged in to the event bus
class MainReactor(
    private val authority: Authority,
    private val entityStore: EntityStore,
    private val searchIndex: SearchIndex,
    private val networkNode: NetworkNode,
    private val addressBook: AddressBook,
) : Reactor {
    private val logger = KotlinLogging.logger("MainReactor")

    private fun handleNodeStarted(event: EventBus.Event){
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
                        if (it.entityId != authority.entityId)
                            launch { requestTransactions(it.entityId) }
                    }
            }
        }
    }

    private fun requestTransactions(peer: Authority) {
        if(peer.entityId == authority.entityId) {
            logger.warn { "Attempt to request transactions from self" }
        }

        var mostRecentTransactionId = entityStore.getLastTransactionId(peer.entityId)

        // TODO - Config max batches
        // TODO: Set reasonable max batches and batch sizes
        for (batch in 1..10000) {
            logger.info { "Requesting transactions from ${peer.name} - Batch $batch" }
            val baseParams = mapOf("authorityId" to peer.entityId.toString())
            val params = if(mostRecentTransactionId == null)
                baseParams
            else
                baseParams.plus(Pair("mostRecentTransactionId", mostRecentTransactionId.toString()))

            val request = Request(Request.Method.GET, "/transactions", null, params)
            val transactionsResponse = networkNode.sendRequest(peer.entityId, request)?.decodeBody<TransactionsResponse>()

            if (transactionsResponse == null || transactionsResponse.transactions.isEmpty()) {
                logger.info { "No transactions received from ${peer.name}" }
                break
            }

            logger.info { "Adding ${transactionsResponse.transactions.count()} transactions from ${peer.name}" }
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

    private fun handleNewTransaction(event: EventBus.Event){
        logger.info { "Handling new transaction" }
        val signedTransaction = ByteArrayInputStream(event.data).use{ SignedTransaction.decode(it) }
        indexTransaction(signedTransaction)

        if (signedTransaction.transaction.authorityId == authority.authorityId) {
            // Transaction originated locally, so inform peers

            val request = request(
                Request.Method.POST,
                "/notifications",
                null,
                null,
                Notification(signedTransaction.transaction.authorityId, PeerEvent.NewTransaction)
            )

            // TODO: authority should come from transaction
            networkNode.broadcastRequest(authority, request)
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

    private fun handlePeerNotification(event: EventBus.Event){
        val notification = ByteArrayInputStream(event.data).use { Notification.decode(it) }
        logger.info { "Handling notification for peer ${notification.peerId} event: ${notification.event}" }

        when(notification.event){
            PeerEvent.Added -> requestTransactions(notification.peerId)
            PeerEvent.Online -> requestTransactions(notification.peerId)
            PeerEvent.NewTransaction -> requestTransactions(notification.peerId)
        }
    }

    private fun handleNodeResume(event: EventBus.Event){
        logger.info { event.name }

        // Restart network node so that connections are fresh
        networkNode.stop()
        networkNode.start()

        // Request any transactions that may have been missed while suspended.
        updatePeerTransactions()
    }
    override fun handleMessage(event: EventBus.Event) {
        logger.info { "Handling event: $event" }

        when(Events.valueOf(event.name)){
            Events.NodeStarted -> handleNodeStarted(event)
            Events.NodeResume -> handleNodeResume(event)
            Events.NewTransaction -> handleNewTransaction(event)
            Events.PeerNotification -> handlePeerNotification(event)
        }
    }
}