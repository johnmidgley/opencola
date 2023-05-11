package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.network.message.GetTransactionsMessage
import io.opencola.network.message.PutTransactionsMessage
import io.opencola.serialization.readByteArray
import io.opencola.serialization.readInt
import io.opencola.serialization.writeByteArray
import io.opencola.serialization.writeInt
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.FileStore
import mu.KotlinLogging
import java.io.ByteArrayOutputStream

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

data class TransactionsResponse(
    val startTransactionId: Id?,
    val currentTransactionId: Id?,
    val transactions: List<SignedTransaction>,
) {
    companion object {
        fun encode(value: TransactionsResponse): ByteArray {
            return ByteArrayOutputStream().use { outputStream ->
                outputStream.writeByteArray(value.startTransactionId?.let { Id.encode(it) } ?: ByteArray(0))
                outputStream.writeByteArray(value.currentTransactionId?.let { Id.encode(it) } ?: ByteArray(0))
                outputStream.writeInt(value.transactions.size)
                value.transactions.forEach { it ->
                    outputStream.write(SignedTransaction.encode(it))
                }
                outputStream.toByteArray()
            }
        }

        fun decode(bytes: ByteArray): TransactionsResponse {
            val inputStream = bytes.inputStream()
            val startTransactionId = inputStream.readByteArray().let { if (it.isEmpty()) null else Id.decode(it) }
            val currentTransactionId = inputStream.readByteArray().let { if (it.isEmpty()) null else Id.decode(it) }
            val numTransactions = inputStream.readInt()
            val transactions = (0 until numTransactions).map { SignedTransaction.decode(inputStream) }
            return TransactionsResponse(startTransactionId, currentTransactionId, transactions)
        }
    }
}

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
    return Route("ping") { _, _, _ -> TODO("Handle ping") }
}

fun pongRoute(): Route {
    return Route("pong") { _, _, _ -> TODO("Handle pong") }
}

fun putNotificationsRoute(eventBus: EventBus, addressBook: AddressBook): Route {
    return Route("notifications") { from, to, message ->
        TODO("handle notifications")
//        handleNotification(addressBook, eventBus, from, to, notification)
    }
}

fun getTransactionsRoute(entityStore: EntityStore, addressBook: AddressBook): Route {
    return Route(
        GetTransactionsMessage.messageType
    ) { from, to, message ->
        val getTransactionsMessage = GetTransactionsMessage.fromPayload(message.body.payload)

        val transactionResponse =  handleGetTransactions(
            entityStore,
            addressBook,
            to,
            from,
            getTransactionsMessage.mostRecentTransactionId,
            getTransactionsMessage.maxTransactions
        )

        TODO("handle getTransactions")
//        val authorityId =
//            Id.decode(request.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
//        val transactionId = request.parameters["mostRecentTransactionId"].nullOrElse { Id.decode(it) }
//        val numTransactions = request.parameters["numTransactions"].nullOrElse { it.toInt() }
//        val transactionResponse =
//            handleGetTransactions(
//                entityStore,
//                addressBook,
//                authorityId,
//                from,
//                transactionId,
//                numTransactions
//            )
    }
}

fun putTransactionsRoute(entityStore: EntityStore, addressBook: AddressBook): Route {
    return Route(
        PutTransactionsMessage.messageType
    ) { from, _, message ->
        val signedTransactions = PutTransactionsMessage(message.body.payload).getSignedTransactions()
        logger.info { "Received ${signedTransactions.size} transactions from $from" }
        entityStore.addSignedTransactions(signedTransactions)
    }
}

fun getDataRoute(fileStore: FileStore): Route {
    return Route(
        "getData"
    ) { _, _, message ->
        TODO("handle getData")
    }
}

fun putDataRoute(fileStore: FileStore): Route {
    return Route(
        "putData"
    ) { _, _, message ->
        TODO("handle putData")
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
        pongRoute(),
        putNotificationsRoute(eventBus, addressBook),
        getTransactionsRoute(entityStore, addressBook),
        putTransactionsRoute(entityStore, addressBook),
        getDataRoute(fileStore),
        putDataRoute(fileStore),
    )
}