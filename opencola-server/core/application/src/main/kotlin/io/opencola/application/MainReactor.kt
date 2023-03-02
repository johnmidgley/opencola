package io.opencola.application

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.event.Reactor
import io.opencola.model.*
import io.opencola.network.*
import io.opencola.storage.AddressBook
import io.opencola.storage.EntityStore
import io.opencola.search.SearchIndex
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.PersonaAddressBookEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.ByteArrayInputStream

// TODO: Invert this by having the reactor subscribe to events vs. being plugged in to the event bus
class MainReactor(
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
        val peers = addressBook.getEntries().filter { it !is PersonaAddressBookEntry && it.isActive }

        if(peers.isNotEmpty()) {
            logger.info { "Updating peer transactions" }

            // TODO: Swap runBlocking with adding to peerExecutor
            runBlocking {
                peers
                    .filter { it !is PersonaAddressBookEntry }
                    .distinctBy { it.entityId }
                    .forEach { launch { requestTransactions(it.entityId) } }
            }
        }
    }

    private fun requestTransactions(peer: AddressBookEntry) {
        if(peer is PersonaAddressBookEntry)
            throw IllegalArgumentException("Attempt to request transactions for local persona: ${peer.entityId}")

        if(!peer.isActive)
            return

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
            val transactionsResponse = networkNode.sendRequest(peer.personaId, peer.entityId, request)?.decodeBody<TransactionsResponse>()

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

    private fun requestTransactions(peerId: Id) {
        //TODO: After requesting transactions, check to see if a new HEAD has been set (i.e. the transactions don't
        // chain to existing ones, which can happen if a peer deletes their store). If this happens, inform the user
        // and ask if "abandoned" transactions should be deleted.
        // TODO: Catch / handle this error and return appropriate forbidden / not authorized status
        // Since a peer can be connected to multiple personas, we arbitrarily pick the first peer
        val peer = addressBook.getEntries().firstOrNull { it.entityId == peerId }
            ?: throw IllegalArgumentException("Attempt to request transactions for unknown peer: $peerId")

        // TODO: This blocks startup. Make fully async (and/or handle startup with event bus)
        // TODO: Remove all runBlocking - replace with appropriate executor
         requestTransactions(peer)
    }

    private fun handleNewTransaction(event: EventBus.Event){
        logger.info { "Handling new transaction" }
        val signedTransaction = ByteArrayInputStream(event.data).use{ SignedTransaction.decode(it) }
        indexTransaction(signedTransaction)

        val authorityId = signedTransaction.transaction.authorityId
        val persona = addressBook.getEntry(authorityId,authorityId) as? PersonaAddressBookEntry

        if(persona != null) {
            // Transaction originated locally, so inform peers
            val request = request(
                Request.Method.POST,
                "/notifications",
                null,
                null,
                Notification(signedTransaction.transaction.authorityId, PeerEvent.NewTransaction)
            )

            // TODO: authority should come from transaction
            networkNode.broadcastRequest(persona, request)
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

        // TODO: A peer that is connected via multiple personas should only send one notification, since broadcasts
        //  are sent to distinct peers. Think about a test for this.

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