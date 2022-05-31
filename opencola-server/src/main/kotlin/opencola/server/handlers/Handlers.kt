package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.network.PeerRouter.PeerStatus.Online
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import opencola.core.storage.EntityStore.TransactionOrder
import opencola.core.storage.MhtCache
import opencola.service.search.SearchService

private val logger = KotlinLogging.logger("Handler")

suspend fun handleGetSearchCall(call: ApplicationCall, searchService: SearchService) {
    val query =
        call.request.queryParameters["q"] ?: throw IllegalArgumentException("No query (q) specified in parameters")
    call.respond(searchService.search(query))
}

@Serializable
data class TransactionsResponse(
    val startTransactionId: Id?,
    val currentTransactionId: Id?,
    val transactions: List<SignedTransaction>
)

//TODO: This should return transactions until the root transaction, not all transactions for the authority in the
// store, as the user a peer may have deleted their store, which creates a new HEAD. Only the transaction for the
// current chain should be propagated to other peers
suspend fun handleGetTransactionsCall(call: ApplicationCall, entityStore: EntityStore, peerRouter: PeerRouter) {
    val authorityId =
        Id.decode(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
    val peerId = Id.decode(call.parameters["peerId"] ?: throw IllegalArgumentException("No peerId set"))
    val transactionId = call.parameters["mostRecentTransactionId"].nullOrElse { Id.decode(it) }

    logger.info { "handleGetTransactionsCall authorityId: $authorityId, peerId: $peerId, transactionId: $transactionId" }

    if(peerRouter.getPeer(peerId) == null){
        logger.error { "Unknown peer attempted to request transactions: $peerId" }
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    val extra = (if (transactionId == null) 0 else 1)
    val numTransactions = (call.parameters["numTransactions"].nullOrElse { it.toInt() } ?: 10) + extra
    val currentTransactionId = entityStore.getLastTransactionId(authorityId)
    val transactions = entityStore.getSignedTransactions(
        authorityId,
        transactionId,
        TransactionOrder.IdAscending,
        numTransactions
    ).drop(extra)

    peerRouter.updateStatus(peerId, Online)
    call.respond(TransactionsResponse(transactionId, currentTransactionId, transactions.toList()))
}

suspend fun handleGetDataCall(call: ApplicationCall, mhtCache: MhtCache, authorityId: Id) {
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val entityId = Id.decode(stringId)

    val data = mhtCache.getData(authorityId, entityId)

    if (data == null) {
        call.respondText(status = HttpStatusCode.NoContent) { "No data for id: $entityId" }
    } else {
        call.respondBytes(data, ContentType.Application.OctetStream)
    }
}

suspend fun handleGetDataPartCall(call: ApplicationCall, authorityId: Id, mhtCache: MhtCache) {
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val partName = call.parameters["partName"] ?: throw IllegalArgumentException("No partName set")

    val bytes = mhtCache.getDataPart(authorityId, Id.decode(stringId), partName)
    if (bytes != null) {
        val contentType = ContentType.fromFilePath(partName).firstOrNull()
        call.respondBytes(bytes, contentType = contentType)
    }
}

// TODO - This should change to handlePeerEvent
suspend fun handlePostNotifications(call: ApplicationCall, addressBook: AddressBook, eventBus: EventBus) {
    val notification = call.receive<PeerRouter.Notification>()
    logger.info { "Received notification: $notification" }

    addressBook.getAuthority(notification.peerId)
        ?: throw IllegalArgumentException("Received notification from unknown peer: ${notification.peerId}")

    eventBus.sendMessage(Events.PeerNotification.toString(), notification.encode())
    call.respond(HttpStatusCode.OK)
}

