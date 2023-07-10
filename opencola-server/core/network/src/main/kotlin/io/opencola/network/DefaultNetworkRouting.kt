package io.opencola.network

import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.network.NetworkNode.*
import io.opencola.network.message.*
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import mu.KotlinLogging
import org.kodein.di.DirectDI
import org.kodein.di.instance

private val logger = KotlinLogging.logger("RequestRouting")

fun handleGetData(fileStore: ContentBasedFileStore, dataId: Id): ByteArray? {
    return fileStore.read(dataId)
}

fun pingRoute(): Route {
    return Route(MessageType.PING) { _, _, _ -> listOf(PongMessage()) }
}

fun pongRoute(handler: messageHandler = { _, _, _ -> emptyList() }): Route {
    return Route(MessageType.PONG, handler)
}

fun getTransactionsRoute(entityStore: EntityStore): Route {
    return Route(MessageType.GET_TRANSACTIONS) { _, to, message ->
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
            // TODO: This doesn't send attachments. Generally, since data is pushed, it's not a big deal, but
            //  when connecting to a new peer, any attachments will be fault filled by feed requests. Think about
            //  PUT ing attachment data in this loop.
            PutTransactionMessage(
                it,
                if (pendingTransactions == 0 && it.transaction.id != lastTransactionId) lastTransactionId else null
            )
        }
    }
}

fun putTransactionRoute(entityStore: EntityStore): Route {
    return Route(MessageType.PUT_TRANSACTION) { from, _, message ->
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

fun getDataRoute(entityStore: EntityStore, fileStore: ContentBasedFileStore): Route {
    return Route(MessageType.GET_DATA) { from, to, message ->
        val getDataMessage = GetDataMessage.decodeProto(message.body.payload)
        val dataId = getDataMessage.id
        logger.info { "getData: from=$from, to=$to, dataId=$dataId" }

        entityStore.getEntity(to, dataId) as? DataEntity
            ?: throw IllegalArgumentException("GetData request from $from to $to for unknown data id: $dataId")

        handleGetData(fileStore, dataId)?.let { PutDataMessage(dataId, it) }?.let { listOf(it) } ?: emptyList()
    }
}

fun putDataRoute(entityStore: EntityStore, fileStore: ContentBasedFileStore): Route {
    return Route(MessageType.PUT_DATA) { from, to, message ->
        val putDataMessage = PutDataMessage.decodeProto(message.body.payload)
        val dataId = putDataMessage.id

        entityStore.getEntity(from, dataId) as? DataEntity
            ?: throw IllegalArgumentException("PutData request from $from to $to for unknown data id: $dataId")
        val id = fileStore.write(putDataMessage.data)
        require(id == putDataMessage.id)
        logger.info { "putData: Wrote dataId=$dataId from=$from, to=$to" }
        emptyList()
    }
}

fun getDefaultRoutes(
    entityStore: EntityStore,
    contentBasedFileStore: ContentBasedFileStore
): List<Route> {
    return listOf(
        pingRoute(),
        pongRoute(),
        getTransactionsRoute(entityStore),
        putTransactionRoute(entityStore),
        getDataRoute(entityStore, contentBasedFileStore),
        putDataRoute(entityStore, contentBasedFileStore),
    )
}

fun getDefaultRoutes(
    di: DirectDI,
): List<Route> {
    return getDefaultRoutes(
        di.instance(),
        di.instance()
    )
}