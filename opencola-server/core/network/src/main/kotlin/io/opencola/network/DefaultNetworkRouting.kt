package io.opencola.network

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.util.nullOrElse
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.storage.AddressBook
import io.opencola.storage.EntityStore
import io.opencola.storage.FileStore

private val logger = KotlinLogging.logger("RequestRouting")

// TODO - This should change to handlePeerEvent
fun handleNotification(addressBook: AddressBook, eventBus: EventBus, fromId: Id, toId: Id, notification: Notification) {
    logger.info { "Received notification: $notification" }

    if (notification.peerId != fromId)
        throw IllegalArgumentException("Notification peerId does not match fromId: ${notification.peerId} != $fromId")

    addressBook.getEntry(toId, fromId)
        ?: throw IllegalArgumentException("Received notification from unknown peer: ${notification.peerId} for $toId")

    eventBus.sendMessage(Events.PeerNotification.toString(), notification.encode())
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
fun handleGetTransactions(
    entityStore: EntityStore,
    addressBook: AddressBook,
    authorityId: Id, // Id of user transactions are being requested for
    peerId: Id, // Id of user making request
    transactionId: Id?,
    numTransactions: Int?,
): TransactionsResponse {
    logger.info { "handleGetTransactionsCall authorityId: $authorityId, peerId: $peerId, transactionId: $transactionId" }

    if (addressBook.getEntry(authorityId, peerId) == null) {
        throw RuntimeException("Unknown peer attempted to request transactions: $peerId")
    }

    // TODO: Check if peer is marked as active?

    val extra = (if (transactionId == null) 0 else 1)
    val totalNumTransactions = (numTransactions ?: 5) + extra
    val currentTransactionId = entityStore.getLastTransactionId(authorityId)
    val transactions = entityStore.getSignedTransactions(
        authorityId,
        transactionId,
        EntityStore.TransactionOrder.IdAscending,
        totalNumTransactions
    ).drop(extra)

    logger.info { "Returning ${transactions.count()} transactions" }
    return TransactionsResponse(transactionId, currentTransactionId, transactions.toList())
}

fun handleGetData(fileStore: FileStore, dataId: Id): ByteArray? {
    return fileStore.read(dataId)
}

fun pingRoute(): Route {
    return Route(
        Request.Method.GET,
        "/ping"
    ) { _, _, _ -> Response(200, "pong") }
}

fun notificationsRoute(eventBus: EventBus, addressBook: AddressBook) : Route {
    return Route(
        Request.Method.POST,
        "/notifications"
    ) { from, to, request ->
        val notification = request.decodeBody<Notification>()
            ?: throw IllegalArgumentException("Body must contain Notification")

        handleNotification(addressBook, eventBus, from, to, notification)
        Response(200)
    }
}

fun transactionsRoute(entityStore: EntityStore, addressBook: AddressBook) : Route {
    return Route(
        Request.Method.GET,
        "/transactions"
    ) { from, _, request ->
        if (request.parameters == null) {
            throw IllegalArgumentException("/transactions call requires parameters")
        }

        val authorityId =
            Id.decode(request.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
        val transactionId = request.parameters["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
        val numTransactions = request.parameters["numTransactions"].nullOrElse { it.toInt() }
        val transactionResponse =
            handleGetTransactions(
                entityStore,
                addressBook,
                authorityId,
                from,
                transactionId,
                numTransactions
            )

        response(200, "OK", null, transactionResponse)
    }
}

fun dataRoute(fileStore: FileStore) : Route {
    return Route(
        Request.Method.GET,
        "/data"
    ) { _, _, request ->
        if (request.parameters == null) {
            throw IllegalArgumentException("/data call requires parameters")
        }

        val dataId = Id.decode(request.parameters["id"] ?: throw IllegalArgumentException("No dataId set"))
        handleGetData(fileStore, dataId)?.let { Response(200, "OK", null, it) }
            ?: Response(404, "Not Found")
    }
}

fun getDefaultRoutes(
    eventBus: EventBus,
    entityStore: EntityStore,
    addressBook: AddressBook,
    fileStore: FileStore,
): List<Route> {

    return listOf(
        pingRoute(),
        notificationsRoute(eventBus, addressBook),
        transactionsRoute(entityStore, addressBook),
        dataRoute(fileStore),
    )
}