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

fun handleGetData(fileStore: ContentBasedFileStore, dataId: Id): ByteArray? {
    return fileStore.read(dataId)
}

fun pingRoute(): Route {
    return Route(PingMessage.messageType) { _, _, _ -> listOf(PongMessage()) }
}

fun pongRoute(handler: messageHandler = { _, _, _ -> emptyList() }): Route {
    return Route(PongMessage.messageType, handler)
}

fun putNotificationsRoute(): Route {
    return Route("notifications") { _, _, _ ->
        TODO("handle notifications")
//        handleNotification(addressBook, eventBus, from, to, notification)
    }
}

fun getTransactionsRoute(entityStore: EntityStore): Route {
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

        val lastTransactionId = entityStore.getLastTransactionId(to)
        var pendingTransactions = signedTransactions.size

        // Generate sequence of PutTransactionMessage messages as response
        signedTransactions.map {
            --pendingTransactions
            PutTransactionMessage(
                it.encodeProto(),
                if (pendingTransactions == 0 && it.transaction.id != lastTransactionId) lastTransactionId else null
            )
        }
    }
}

fun putTransactionsRoute(entityStore: EntityStore): Route {
    return Route(
        PutTransactionMessage.messageType
    ) { from, _, message ->
        val putTransactionsMessage = PutTransactionMessage.decodeProto(message.body.payload)
        val signedTransaction = putTransactionsMessage.getSignedTransaction()
        logger.info { "Received transaction ${signedTransaction.transaction.id} from $from" }
        entityStore.addSignedTransaction(signedTransaction)

        if (putTransactionsMessage.lastTransactionId != null) {
            // Peer has indicated that it is appropriate to request more transactions (i.e. this is the last
            // transaction in response to a getTransactions request), so check if we need to request more transactions.
            val peerMostRecentTransactionId = entityStore.getLastTransactionId(from)
            if (putTransactionsMessage.lastTransactionId != peerMostRecentTransactionId)
                return@Route listOf(GetTransactionsMessage(peerMostRecentTransactionId))
        }

        emptyList()
    }
}

fun getDataRoute(fileStore: ContentBasedFileStore): Route {
    return Route(
        GetDataMessage.messageType
    ) { _, _, message ->
        val getDataMessage = GetDataMessage.decodeProto(message.body.payload)
        val dataId = getDataMessage.id
        handleGetData(fileStore, dataId)?.let { PutDataMessage(dataId, it) }?.let { listOf(it) } ?: emptyList()
    }
}

fun putDataRoute(fileStore: ContentBasedFileStore): Route {
    return Route(
        PutDataMessage.messageType
    ) { _, _, message ->
        val putDataMessage = PutDataMessage.decodeProto(message.body.payload)
        val id = fileStore.write(putDataMessage.data)
        require(id == putDataMessage.id)
        emptyList()
    }
}

fun getDefaultRoutes(
    di: DirectDI,
): List<Route> {
    return listOf(
        pingRoute(),
        pongRoute(),
        putNotificationsRoute(),
        getTransactionsRoute(di.instance()),
        putTransactionsRoute(di.instance()),
        getDataRoute(di.instance()),
        putDataRoute(di.instance()),
    )
}