package io.opencola.network

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.Id
import io.opencola.model.SignedTransaction
import io.opencola.network.NetworkNode.*
import io.opencola.network.message.*
import io.opencola.serialization.readByteArray
import io.opencola.serialization.readInt
import io.opencola.serialization.writeByteArray
import io.opencola.serialization.writeInt
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import mu.KotlinLogging
import org.kodein.di.DirectDI
import org.kodein.di.instance
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

fun handleGetData(fileStore: ContentBasedFileStore, dataId: Id): ByteArray? {
    return fileStore.read(dataId)
}

fun pingRoute(): Route {
    return Route(PingMessage.messageType) { _, _, _ -> PongMessage() }
}

fun pongRoute(handler: messageHandler =  { _, _, _ -> null }): Route {
    return Route(PongMessage.messageType, handler)
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
    ) { _, to, message ->
        val getTransactionsMessage = GetTransactionsMessage.decodeProto(message.body.payload)

        val extra = (if (getTransactionsMessage.mostRecentTransactionId == null) 0 else 1)
        // TODO: Add local limit on max transactions
        val totalNumTransactions = (getTransactionsMessage.maxTransactions) + extra
        val signedTransactions = entityStore.getSignedTransactions(
            to,
            getTransactionsMessage.mostRecentTransactionId,
            EntityStore.TransactionOrder.IdAscending,
            totalNumTransactions
        ).drop(extra)

        PutTransactionsMessage(signedTransactions.map { it.encodeProto() })
    }
}

fun putTransactionsRoute(entityStore: EntityStore, addressBook: AddressBook): Route {
    return Route(
        PutTransactionsMessage.messageType
    ) { from, _, message ->
        val putTransactionsMessage = PutTransactionsMessage.decodeProto(message.body.payload)
        val signedTransactions = putTransactionsMessage.getSignedTransactions()
        logger.info { "Received ${signedTransactions.size} transactions from $from" }
        entityStore.addSignedTransactions(signedTransactions)
        null
    }
}

fun getDataRoute(fileStore: ContentBasedFileStore): Route {
    return Route(
        GetDataMessage.messageType
    ) { _, _, message ->
        val getDataMessage = GetDataMessage.decodeProto(message.body.payload)
        val dataId = getDataMessage.id
        handleGetData(fileStore, dataId)?.let { PutDataMessage(dataId, it) }
    }
}

fun putDataRoute(fileStore: ContentBasedFileStore): Route {
    return Route(
        PutDataMessage.messageType
    ) { _, _, message ->
        val putDataMessage = PutDataMessage.decodeProto(message.body.payload)
        val id =  fileStore.write(putDataMessage.data)
        require(id == putDataMessage.id)
        null
    }
}

fun getDefaultRoutes(
    di: DirectDI,
): List<Route> {
    return listOf(
        pingRoute(),
        pongRoute(),
        putNotificationsRoute(di.instance(), di.instance()),
        getTransactionsRoute(di.instance(), di.instance()),
        putTransactionsRoute(di.instance(), di.instance()),
        getDataRoute(di.instance()),
        putDataRoute(di.instance()),
    )
}