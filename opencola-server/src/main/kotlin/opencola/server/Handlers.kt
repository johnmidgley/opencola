package opencola.server

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
import opencola.core.network.PeerRouter.PeerStatus.Status.Online
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.core.storage.EntityStore.TransactionOrder
import opencola.core.storage.MhtCache
import opencola.service.EntityResult
import opencola.service.EntityResult.Activity
import opencola.service.EntityResult.Summary
import opencola.service.search.SearchService
import java.net.URI

private val logger = KotlinLogging.logger("Handler")

suspend fun handleGetSearchCall(call: ApplicationCall, searchService: SearchService) {
    val query =
        call.request.queryParameters["q"] ?: throw IllegalArgumentException("No query (q) specified in parameters")
    call.respond(searchService.search(query))
}

suspend fun handleGetEntityCall(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entity = entityStore.getEntity(authorityId, Id.fromHexString(stringId))

    if (entity != null)
        call.respond(entity.getFacts())
}

suspend fun handleGetEntityCall(call: ApplicationCall, entityStore: EntityStore) {
    val authorityId = Id.fromHexString(
        call.parameters["authorityId"]
            ?: throw IllegalArgumentException("No authority id specified")
    )

    handleGetEntityCall(call, authorityId, entityStore)
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

    peerRouter.updateStatus(peerId, Online)
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

@Serializable
data class FeedResult(val pagingToken: String?, val results: List<EntityResult>) {
    constructor(transactionId: Id?, results: List<EntityResult>) : this(transactionId?.toString(), results)
}

fun stringAttributeFromFacts(facts: List<Fact>, attribute: Attribute): String? {
    return getFact(facts, attribute).nullOrElse { attribute.codec.decode(it.value.bytes).toString() }
}

fun getSummary(facts: List<Fact>): Summary {
    return Summary(
        stringAttributeFromFacts(facts, CoreAttribute.Name.spec),
        stringAttributeFromFacts(facts, CoreAttribute.Uri.spec)!!,
        stringAttributeFromFacts(facts, CoreAttribute.Description.spec),
        stringAttributeFromFacts(facts, CoreAttribute.ImageUri.spec)
    )
}

fun getActivity(authorityId: Id, name: String, epochSecond: Long, save: Boolean?, trust: Float?, like: Boolean?, rating: Float?): Activity? {
    return if (listOf(save, trust, like, rating).all { it == null })
        null
    else
        Activity(authorityId, name, epochSecond, Actions(save, trust, like, rating))
}

fun getActorName(id: Id, rootAuthority: Authority, peerRouter: PeerRouter): String {
    return if (id == rootAuthority.authorityId)
        "You"
    else
        peerRouter.getPeer(id)?.name ?: "Unknown"


}

fun getFact(facts: Iterable<Fact>, attribute: Attribute): Fact? {
    return facts
        .filter { it.operation != Operation.Retract }
        .lastOrNull { it.attribute == attribute }
}

fun getAttributeValueFromFact(facts: Iterable<Fact>, attribute: Attribute): Any? {
    return getFact(facts, attribute).nullOrElse { attribute.codec.decode(it.value.bytes) }
}

fun getActivityFromFacts(authorityId: Id, name: String, epochSecond: Long, facts: Iterable<Fact>): Activity? {
    return getActivity(
        authorityId,
        name,
        epochSecond,
        getFact(facts, CoreAttribute.Uri.spec).nullOrElse { true },
        getAttributeValueFromFact(facts, CoreAttribute.Trust.spec) as Float?,
        getAttributeValueFromFact(facts, CoreAttribute.Like.spec) as Boolean?,
        getAttributeValueFromFact(facts, CoreAttribute.Rating.spec) as Float?
    )
}

fun getEntityActivitiesFromFacts(entityFacts: Iterable<Fact>, idToName: (Id) -> String): List<Activity> {
    if(entityFacts.distinctBy { it.entityId }.size != 1){
        throw IllegalArgumentException("Attempt to get activities from facts with multiple entities")
    }

    return entityFacts
        .sortedByDescending { it.epochSecond }
        .groupBy { Pair(it.authorityId, it.epochSecond!!) }
        .map {
            val (authorityId, epochSecond) = it.key
            getActivityFromFacts(authorityId, idToName(authorityId), epochSecond, it.value)
        }.filterNotNull()
}

fun isEntityIsVisible(entity: Entity?) : Boolean{
    return when(entity){
        is ResourceEntity -> entity.like != false
        is ActorEntity -> entity.like != false
        else -> false
    }
}

fun getEntityFacts(entityStore: EntityStore, entityIds: Iterable<Id>): Map<Id, List<Fact>> {
    return entityStore.getFacts(emptyList(), entityIds)
        .groupBy { it.entityId }
        .filter { isEntityIsVisible( Entity.fromFacts(it.value)) }
        .toMap()
}

fun getEntityIds(entityStore: EntityStore, searchIndex: SearchIndex, query: String?): List<Id> {
    return if (query == null || query.trim().isEmpty()){
        val signedTransactions = entityStore.getSignedTransactions(emptyList(),null, TransactionOrder.Descending,100) // TODO: Config limit
        signedTransactions.flatMap { tx -> tx.transaction.transactionEntities.map { it.entityId } }.distinct()
    } else {
        searchIndex.search(query).map { it.entityId }
    }
}

suspend fun handleGetFeed(call: ApplicationCall, authority: Authority, entityStore: EntityStore, searchIndex: SearchIndex, peerRouter: PeerRouter) {
    // TODO: Look for startTransactionId in call (For paging)
    val entityIds = getEntityIds(entityStore, searchIndex, call.parameters["q"])
    val entityFactsById = getEntityFacts(entityStore, entityIds)
    val idToName: (Id) -> String = { id -> getActorName(id, authority, peerRouter) }
    val entityActivitiesById = entityFactsById.entries.associate { Pair(it.key, getEntityActivitiesFromFacts(it.value, idToName)) }

    call.respond(FeedResult(
        "",
        entityIds
            .filter { entityFactsById.containsKey(it) }
            .map { EntityResult(it, getSummary(entityFactsById[it]!!), entityActivitiesById[it]!!) }
    ))
}