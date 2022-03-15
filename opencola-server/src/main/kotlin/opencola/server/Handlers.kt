package opencola.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.content.parseMhtml
import opencola.core.extensions.nullOrElse
import opencola.core.model.Actions
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.model.SignedTransaction
import opencola.core.network.PeerRouter
import opencola.core.network.PeerRouter.PeerStatus.Status.*
import opencola.core.storage.EntityStore
import opencola.core.storage.MhtCache
import opencola.service.EntityService
import opencola.service.search.SearchService
import java.net.URI

suspend fun handleGetSearchCall(call: ApplicationCall, searchService: SearchService) {
    val query = call.request.queryParameters["q"] ?: throw IllegalArgumentException("No query (q) specified in parameters")
    call.respond(searchService.search(query))
}

suspend fun handleGetEntityCall(call: ApplicationCall, authorityId: Id, entityStore: EntityStore){
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entity = entityStore.getEntity(authorityId, Id.fromHexString(stringId))

    if(entity != null)
        call.respond(entity.getFacts())
}

suspend fun handleGetEntityCall(call: ApplicationCall, entityStore: EntityStore){
    val authorityId = Id.fromHexString(call.parameters["authorityId"]
        ?: throw IllegalArgumentException("No authority id specified"))

    handleGetEntityCall(call, authorityId, entityStore)
}

@Serializable
data class TransactionsResponse(
    val startTransactionId: Id?,
    val currentTransactionId: Id?,
    val transactions: List<SignedTransaction>)

suspend fun handleGetTransactionsCall(call: ApplicationCall, entityStore: EntityStore){
    val authorityId = Id.fromHexString(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
    val transactionId = call.parameters["mostRecentTransactionId"].nullOrElse { Id.fromHexString(it) }
    val extra = (if (transactionId == null) 0 else 1)
    val numTransactions =(call.parameters["numTransactions"].nullOrElse { it.toInt() } ?: 10) + extra
    val currentTransactionId = entityStore.getLastTransactionId(authorityId)
    val transactions = entityStore.getTransactions(authorityId, transactionId, numTransactions + 1).drop(extra)

    // TODO: Getting a request is a sign the the remote host is up - update the peer status in the PeerService
    call.respond(TransactionsResponse(transactionId, currentTransactionId, transactions.toList()))
}

suspend fun handleGetDataCall(call: ApplicationCall, mhtCache: MhtCache, authorityId: Id){
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val entityId = Id.fromHexString(stringId)

    val data = mhtCache.getData(authorityId, entityId)

    if(data == null){
        call.respondText(status = HttpStatusCode.NoContent) { "No data for id: $entityId" }
    } else {
        call.respondBytes(data, ContentType.Application.OctetStream)
    }
}

suspend fun handleGetDataPartCall(call: ApplicationCall, authorityId: Id, mhtCache: MhtCache){
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val partName = call.parameters["partName"] ?: throw IllegalArgumentException("No partName set")

    val bytes = mhtCache.getDataPart(authorityId, Id.fromHexString(stringId), partName)
    if(bytes != null){
        val contentType = ContentType.fromFilePath(partName).firstOrNull()
        call.respondBytes(bytes, contentType = contentType)
    }
}

fun handleAction(action: String, value: String?, entityService: EntityService, mhtml: ByteArray) {
    val mhtmlPage = mhtml.inputStream().use { parseMhtml(it) ?: throw RuntimeException("Unable to parse mhtml") }

    val actions = when(action){
        "save" -> Actions()
        "like" -> Actions(like = value?.toBooleanStrict() ?: throw RuntimeException("No value specified for like"))
        "trust" -> Actions(trust = value?.toFloat() ?: throw RuntimeException("No value specified for trust"))
        else -> throw NotImplementedError("No handler for $action")
    }

    entityService.updateResource(mhtmlPage, actions)
}

suspend fun handlePostActionCall(call: ApplicationCall, entityService: EntityService){
    val multipart = call.receiveMultipart()
    var action: String? = null
    var value: String? = null
    var mhtml: ByteArray? = null

    multipart.forEachPart { part ->
        when(part){
            is PartData.FormItem -> {
                when(part.name){
                    "action" -> action = part.value
                    "value" -> value = part.value
                    else -> throw IllegalArgumentException("Unknown FormItem in action request: ${part.name}")
                }
            }
            is PartData.FileItem -> {
                if(part.name != "mhtml") throw IllegalArgumentException("Unknown FileItem in action request: ${part.name}")
                mhtml = part.streamProvider().use { it.readAllBytes() }
            }
            else -> throw IllegalArgumentException("Unknown part in request: ${part.name}")
        }
    }

    if(action == null){
        throw IllegalArgumentException("No action specified for request")
    }

    if(value == null){
        throw IllegalArgumentException("No value specified for request")
    }

    if(mhtml == null){
        throw IllegalArgumentException("No mhtml specified for request")
    }

    handleAction(action as String, value, entityService, mhtml as ByteArray)
    call.respond(HttpStatusCode.Accepted)
}

suspend fun handleGetActionsCall(call: ApplicationCall, authorityId: Id, entityStore: EntityStore){
    val stringUri = call.parameters["uri"] ?: throw IllegalArgumentException("No uri set")
    val entityId = Id.ofUri(URI(stringUri))
    val entity = entityStore.getEntity(authorityId, entityId) as? ResourceEntity

    if(entity != null){
        call.respond(Actions(entity.trust, entity.like, entity.rating))
    }
}

suspend fun handlePostNotifications(call: ApplicationCall, entityService: EntityService, peerRouter: PeerRouter){
    val notification = call.receive<PeerRouter.Notification>()
    val peerId = notification.peerId

    // TODO: Handle switch to online with event bus that triggers request for new transactions
    peerRouter.updateStatus(peerId, Online)

    when(notification.event){
        PeerRouter.Event.NewTransactions -> entityService.requestTransactions(peerId)
    }

    call.respond(HttpStatusCode.OK)
}