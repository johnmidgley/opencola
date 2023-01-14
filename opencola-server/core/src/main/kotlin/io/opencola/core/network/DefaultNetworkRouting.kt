package io.opencola.core.network

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.core.event.EventBus
import io.opencola.core.event.Events
import io.opencola.core.extensions.nullOrElse
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.core.storage.AddressBook
import io.opencola.core.storage.EntityStore

private val logger = KotlinLogging.logger("RequestRouting")

// TODO - This should change to handlePeerEvent
fun handleNotification(addressBook: AddressBook, eventBus: EventBus, notification: Notification) {
    logger.info { "Received notification: $notification" }

    addressBook.getAuthority(notification.peerId)
        ?: throw IllegalArgumentException("Received notification from unknown peer: ${notification.peerId}")

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

    if(addressBook.getAuthority(peerId) == null){
        throw RuntimeException("Unknown peer attempted to request transactions: $peerId")
    }

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

fun getDefaultRoutes(
    eventBus: EventBus,
    entityStore: EntityStore,
    addressBook: AddressBook,
): List<Route> {

    return listOf(
        Route(
            Request.Method.GET,
            "/ping"
        ) { _, _, _ -> Response(200, "pong") },
        Route(
            Request.Method.POST,
            "/notifications"
        ) { _, _, request ->
            val notification = request.decodeBody<Notification>()
                ?: throw IllegalArgumentException("Body must contain Notification")

            handleNotification(addressBook, eventBus, notification)
            Response(200)
        },

        Route(
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
    )
}