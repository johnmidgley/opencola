package io.opencola.application

import io.opencola.event.bus.Event
import io.opencola.event.bus.Events
import io.opencola.event.bus.Reactor
import io.opencola.model.*
import io.opencola.network.*
import io.opencola.network.message.GetDataMessage
import io.opencola.network.message.GetTransactionsMessage
import io.opencola.network.message.PutDataMessage
import io.opencola.network.message.PutTransactionMessage
import io.opencola.search.SearchIndex
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.filestore.ContentAddressedFileStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import io.opencola.model.protobuf.Model as ProtoModel

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

    logger.info { "Indexed transaction: ${signedTransaction.transaction.id}" }
}

// TODO: Invert this by having the reactor subscribe to events vs. being plugged in to the event bus
class MainReactor(
    private val networkConfig: NetworkConfig,
    private val entityStore: EntityStore,
    private val searchIndex: SearchIndex,
    private val networkNode: NetworkNode,
    private val addressBook: AddressBook,
    private val fileStore: ContentAddressedFileStore,
) : Reactor {
    private fun handleNodeStarted(event: Event) {
        logger.info { event.name }
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

        val senderCurrentTransactionId = entityStore.getLastTransactionId(peer.personaId)
        val receiverCurrentTransactionId = entityStore.getLastTransactionId(peer.entityId)

        networkNode.sendMessage(
            peer.personaId,
            peer.entityId,
            GetTransactionsMessage(senderCurrentTransactionId, receiverCurrentTransactionId)
        )

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

    private fun broadcastAttachments(persona: PersonaAddressBookEntry, signedTransaction: SignedTransaction) {
        // TODO: Optimize by filtering out peers that already have the data id
        signedTransaction.transaction.transactionEntities
            .flatMap { e -> e.facts.filter { f -> f.attribute == CoreAttribute.AttachmentIds.spec && f.operation == Operation.Add } }
            .map { f -> f.value.get() as Id }
            .forEach { dataId ->
                fileStore.read(dataId)
                    ?.let { data -> networkNode.broadcastMessage(persona, PutDataMessage(dataId, data)) }
                    ?: logger.warn { "Could not find attachment in filestore - unable to send: $dataId" }
            }
    }

    private fun handleNewTransaction(event: Event) {
        logger.info { "Handling new transaction" }
        val signedTransaction = SignedTransaction.fromProto(ProtoModel.SignedTransaction.parseFrom(event.data))
        indexTransaction(signedTransaction)

        val authorityId = signedTransaction.transaction.authorityId
        val persona = addressBook.getEntry(authorityId, authorityId) as? PersonaAddressBookEntry

        if (persona != null) {
            // Transaction originated locally, so inform peers. Put any attachments first so that they're available
            // when the transaction is processed
            broadcastAttachments(persona, signedTransaction)
            networkNode.broadcastMessage(persona, PutTransactionMessage(signedTransaction))
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
            else -> logger.warn { "Ignoring notification for peer ${notification.peerId} event: ${notification.event}" }
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

    private fun handleNoPendingNetworkMessages(event: Event) {
        val noPendingMessagesEvent = NoPendingMessagesEvent.decode(event.data)
        logger.info { "Handling event: $noPendingMessagesEvent" }

        val peers = addressBook.getPeers()

        // Get all peer ids that are connected to the persona and address
        val peerIds = peers
            .filter { it.personaId == noPendingMessagesEvent.personaId && it.address == noPendingMessagesEvent.address }
            .map { it.entityId }
            .toSet()

        // A peer can be connected with multiple personas. To avoid duplicating transaction requests,
        // we use the address book ordering to only request from the first persona in address book.
        // TODO: Add test for this
        peers
            .filter { it.entityId in peerIds }
            .distinctBy { it.entityId } // This ensures that requests are only sent from a single persona
            .filter { it.personaId == noPendingMessagesEvent.personaId && it.address == noPendingMessagesEvent.address }
            .forEach { requestTransactions(it) }
    }

    private fun handleDataMissing(event: Event) {
        val dataId = Id.decodeProto(event.data)
        val peers = addressBook.getPeers()
        val peersById = peers.associateBy { it.entityId }
        val peerIds = peers.map { it.entityId }.toSet()

        val peerDataEntities = entityStore.getEntities(peerIds, setOf(dataId))

        if (peerDataEntities.isEmpty()) {
            logger.warn { "Unable to find remote DataEntity for: $dataId" }
        } else {
            val message = GetDataMessage(dataId)
            peerDataEntities.forEach { entity ->
                // NOTE: This requests data from all peers. Inefficient, but better user experience.
                peersById[entity.authorityId]?.let {
                    networkNode.sendMessage(it.personaId, entity.authorityId, message)
                } ?: logger.warn { "Missing peer: ${entity.authorityId}" }
            }
        }

        logger.info { "Handled missing data event for: $dataId" }
    }


    override fun handleMessage(event: Event) {
        logger.info { "Handling event: $event" }

        when (Events.valueOf(event.name)) {
            Events.NodeStarted -> handleNodeStarted(event)
            Events.NodeResume -> handleNodeResume(event)
            Events.NewTransaction -> handleNewTransaction(event)
            Events.PeerNotification -> handlePeerNotification(event)
            Events.NoPendingNetworkMessages -> handleNoPendingNetworkMessages(event)
            Events.DataMissing -> handleDataMissing(event)
        }
    }
}