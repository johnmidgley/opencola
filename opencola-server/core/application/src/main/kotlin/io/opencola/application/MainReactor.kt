package io.opencola.application

import io.opencola.event.Event
import io.opencola.event.Events
import io.opencola.event.Reactor
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.network.NetworkConfig
import io.opencola.network.NetworkNode
import io.opencola.network.Notification
import io.opencola.network.PeerEvent
import io.opencola.network.message.GetTransactionsMessage
import io.opencola.network.message.PutTransactionsMessage
import io.opencola.search.SearchIndex
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import io.opencola.serialization.protobuf.Model as ProtoModel

private val logger = KotlinLogging.logger("MainReactor")

fun indexTransaction(
    entityStore: EntityStore,
    searchIndex: SearchIndex,
    signedTransaction: SignedTransaction
) {
    logger.info { "Indexing transaction: ${signedTransaction.transaction.id}" }
    val authorityId = signedTransaction.transaction.authorityId

    signedTransaction.transaction.transactionEntities
        .map { f -> f.entityId }
        .distinct()
        .forEach { entityId ->
            val entity = entityStore.getEntity(authorityId, entityId)

            if (entity == null) {
                // Entity was deleted
                searchIndex.deleteEntities(authorityId, entityId)
            } else if (entity !is DataEntity) { // TODO: Should data entities be indexed?
                //TODO: Archive will not be available - figure out what to do
                // Call peer for data?
                searchIndex.addEntities(entity)
            }
        }
}

// TODO: Invert this by having the reactor subscribe to events vs. being plugged in to the event bus
class MainReactor(
    private val networkConfig: NetworkConfig,
    private val entityStore: EntityStore,
    private val searchIndex: SearchIndex,
    private val networkNode: NetworkNode,
    private val addressBook: AddressBook,
) : Reactor {
    private fun handleNodeStarted(event: Event) {
        logger.info { event.name }
        updatePeerTransactions()
    }

    private fun updatePeerTransactions() {
        if (networkConfig.offlineMode) return

        val peers = addressBook.getEntries().filter { it !is PersonaAddressBookEntry && it.isActive }

        if (peers.isNotEmpty()) {
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
        if (networkConfig.offlineMode) return

        if (peer is PersonaAddressBookEntry)
            throw IllegalArgumentException("Attempt to request transactions for local persona: ${peer.entityId}")

        if (!peer.isActive)
            return

        val mostRecentTransactionId = entityStore.getLastTransactionId(peer.entityId)
        networkNode.sendMessage(peer.personaId, peer.entityId, GetTransactionsMessage(mostRecentTransactionId))

//        // TODO - Config max batches
//        // TODO: Set reasonable max batches and batch sizes
//        for (batch in 1..10000) {
//            logger.info { "Requesting transactions from ${peer.name} - Batch $batch" }
//            val baseParams = mapOf("authorityId" to peer.entityId.toString())
//            val params = if (mostRecentTransactionId == null)
//                baseParams
//            else
//                baseParams.plus(Pair("mostRecentTransactionId", mostRecentTransactionId.toString()))
//
//            val request = Request(Request.Method.GET, "/transactions", null, params)
//            val transactionsResponse = networkNode.sendMessage(peer.personaId, peer.entityId, request)?.body?.let {
//             TransactionsResponse.decode(it)
//            }
//
//            if (transactionsResponse == null || transactionsResponse.transactions.isEmpty()) {
//                logger.info { "No transactions received from ${peer.name}" }
//                break
//            }
//
//            logger.info { "Adding ${transactionsResponse.transactions.count()} transactions from ${peer.name}" }
//            entityStore.addSignedTransactions(transactionsResponse.transactions)
//            mostRecentTransactionId = transactionsResponse.transactions.last().transaction.id
//
//            if (mostRecentTransactionId == transactionsResponse.currentTransactionId)
//                break
//        }

        logger.info { "Completed requesting transactions from: ${peer.name}" }
    }

    private fun requestTransactions(peerId: Id) {
        if (networkConfig.offlineMode) return

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

    private fun handleNewTransaction(event: Event) {
        logger.info { "Handling new transaction" }
        val signedTransaction = SignedTransaction.fromProto(ProtoModel.SignedTransaction.parseFrom(event.data))
        indexTransaction(signedTransaction)

        val authorityId = signedTransaction.transaction.authorityId
        val persona = addressBook.getEntry(authorityId, authorityId) as? PersonaAddressBookEntry

        if (persona != null) {
            // Transaction originated locally, so inform peers
            networkNode.broadcastMessage(persona, PutTransactionsMessage(listOf(event.data)))
        }
    }

    private fun indexTransaction(signedTransaction: SignedTransaction) {
        indexTransaction(entityStore, searchIndex, signedTransaction)
    }

    private fun handlePeerNotification(event: Event) {
        val notification = ByteArrayInputStream(event.data).use { Notification.decode(it) }
        logger.info { "Handling notification for peer ${notification.peerId} event: ${notification.event}" }

        // TODO: A peer that is connected via multiple personas should only send one notification, since broadcasts
        //  are sent to distinct peers. Think about a test for this.

        when (notification.event) {
            PeerEvent.Added -> requestTransactions(notification.peerId)
            PeerEvent.Online -> requestTransactions(notification.peerId)
            PeerEvent.NewTransaction -> requestTransactions(notification.peerId)
        }
    }

    private fun handleNodeResume(event: Event) {
        logger.info { event.name }

        // Restart network node so that connections are fresh
        networkNode.stop()
        networkNode.start()

        // Request any transactions that may have been missed while suspended.
        updatePeerTransactions()
    }

    override fun handleMessage(event: Event) {
        logger.info { "Handling event: $event" }

        when (Events.valueOf(event.name)) {
            Events.NodeStarted -> handleNodeStarted(event)
            Events.NodeResume -> handleNodeResume(event)
            Events.NewTransaction -> handleNewTransaction(event)
            Events.PeerNotification -> handlePeerNotification(event)
        }
    }
}