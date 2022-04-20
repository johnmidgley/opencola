package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.content.parseMhtml
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.storage.EntityStore
import opencola.core.storage.EntityStore.TransactionOrder
import opencola.core.storage.MhtCache
import opencola.service.search.SearchService
import java.net.URI

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
suspend fun handleGetTransactionsCall(call: ApplicationCall, entityStore: EntityStore, eventBus: EventBus) {
    val authorityId =
        Id.fromHexString(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
    val peerId = Id.fromHexString(call.parameters["peerId"] ?: throw IllegalArgumentException("No peerId set"))
    val transactionId = call.parameters["mostRecentTransactionId"].nullOrElse { Id.fromHexString(it) }

    logger.info { "handleGetTransactionsCall authorityId: $authorityId, peerId: $peerId, transactionId: $transactionId" }

    val extra = (if (transactionId == null) 0 else 1)
    val numTransactions = (call.parameters["numTransactions"].nullOrElse { it.toInt() } ?: 10) + extra
    val currentTransactionId = entityStore.getLastTransactionId(authorityId)
    val transactions = entityStore.getSignedTransactions(
        authorityId,
        transactionId,
        TransactionOrder.Ascending,
        numTransactions
    ).drop(extra)

    // TODO: This is sending unnecessary events (if we know the peer is already online). Check peer status here?
    eventBus.sendMessage(Events.PeerNotification.toString(), PeerRouter.Notification(peerId, PeerRouter.Event.Online).encode())
    call.respond(TransactionsResponse(transactionId, currentTransactionId, transactions.toList()))

}

suspend fun handleGetDataCall(call: ApplicationCall, mhtCache: MhtCache, authorityId: Id) {
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val entityId = Id.fromHexString(stringId)

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

    val bytes = mhtCache.getDataPart(authorityId, Id.fromHexString(stringId), partName)
    if (bytes != null) {
        val contentType = ContentType.fromFilePath(partName).firstOrNull()
        call.respondBytes(bytes, contentType = contentType)
    }
}


fun handleAction(action: String, value: String?, entityStore: EntityStore, mhtml: ByteArray) {
    val mhtmlPage = mhtml.inputStream().use { parseMhtml(it) ?: throw RuntimeException("Unable to parse mhtml") }

    val actions = when (action) {
        "save" -> Actions(save = true)
        "like" -> Actions(like = value?.toBooleanStrict() ?: throw RuntimeException("No value specified for like"))
        "trust" -> Actions(trust = value?.toFloat() ?: throw RuntimeException("No value specified for trust"))
        else -> throw NotImplementedError("No handler for $action")
    }

    entityStore.updateResource(mhtmlPage, actions)
}

suspend fun handlePostActionCall(call: ApplicationCall, entityStore: EntityStore) {
    val multipart = call.receiveMultipart()
    var action: String? = null
    var value: String? = null
    var mhtml: ByteArray? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                when (part.name) {
                    "action" -> action = part.value
                    "value" -> value = part.value
                    else -> throw IllegalArgumentException("Unknown FormItem in action request: ${part.name}")
                }
            }
            is PartData.FileItem -> {
                if (part.name != "mhtml") throw IllegalArgumentException("Unknown FileItem in action request: ${part.name}")
                mhtml = part.streamProvider().use { it.readAllBytes() }
            }
            else -> throw IllegalArgumentException("Unknown part in request: ${part.name}")
        }
    }

    if (action == null) {
        throw IllegalArgumentException("No action specified for request")
    }

    if (value == null) {
        throw IllegalArgumentException("No value specified for request")
    }

    if (mhtml == null) {
        throw IllegalArgumentException("No mhtml specified for request")
    }

    handleAction(action as String, value, entityStore, mhtml as ByteArray)
    call.respond(HttpStatusCode.Accepted)
}

suspend fun handleGetActionsCall(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    val stringUri = call.parameters["uri"] ?: throw IllegalArgumentException("No uri set")
    val entityId = Id.ofUri(URI(stringUri))
    val entity = entityStore.getEntity(authorityId, entityId) as? ResourceEntity

    if (entity != null) {
        call.respond(Actions(true, entity.trust, entity.like, entity.rating))
    }
}

// TODO - This should change to handlePeerEvent
suspend fun handlePostNotifications(call: ApplicationCall, eventBus: EventBus) {
    val notification = call.receive<PeerRouter.Notification>()
    eventBus.sendMessage(Events.PeerNotification.toString(), notification.encode())
    call.respond(HttpStatusCode.OK)
}

