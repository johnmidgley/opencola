package opencola.server

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.content.parseMhtml
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.model.Transaction.TransactionFact
import opencola.core.network.PeerRouter
import opencola.core.network.PeerRouter.PeerStatus.Status.Online
import opencola.core.storage.EntityStore
import opencola.core.storage.MhtCache
import opencola.service.EntityResult
import opencola.service.EntityResult.Activity
import opencola.service.EntityResult.Summary
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

suspend fun handleGetTransactionsCall(call: ApplicationCall, entityStore: EntityStore, peerRouter: PeerRouter){
    val authorityId = Id.fromHexString(call.parameters["authorityId"] ?: throw IllegalArgumentException("No authorityId set"))
    val peerId = Id.fromHexString(call.parameters["peerId"] ?: throw IllegalArgumentException("No peerId set"))
    val transactionId = call.parameters["mostRecentTransactionId"].nullOrElse { Id.fromHexString(it) }
    val extra = (if (transactionId == null) 0 else 1)
    val numTransactions =(call.parameters["numTransactions"].nullOrElse { it.toInt() } ?: 10) + extra
    val currentTransactionId = entityStore.getLastTransactionId(authorityId)
    val transactions = entityStore.getSignedTransactions(authorityId, transactionId, EntityStore.TransactionOrder.Ascending, numTransactions + 1).drop(extra)

    peerRouter.updateStatus(peerId, Online)
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
        "save" -> Actions(save = true)
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
        call.respond(Actions(true, entity.trust, entity.like, entity.rating))
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

@Serializable
data class FeedResult(val transactionId: String?, val results: List<EntityResult>){
    constructor(transactionId: Id?, results: List<EntityResult>) : this(transactionId?.toString(), results)
}

fun stringAttributeFromFacts(facts: List<Fact>, attribute: Attribute): String? {
    return facts
        .firstOrNull{ it.attribute == attribute }
        .nullOrElse { attribute.codec.decode(it.value.bytes).toString() }
}

fun getSummary(facts: List<Fact>): Summary {
    return Summary(
        stringAttributeFromFacts(facts, CoreAttribute.Name.spec),
        stringAttributeFromFacts(facts, CoreAttribute.Uri.spec)!!,
        stringAttributeFromFacts(facts, CoreAttribute.Description.spec),
        stringAttributeFromFacts(facts, CoreAttribute.ImageUri.spec)
    )
}

fun getActivity(authorityId: Id, name: String, epochSecond: Long, fact: TransactionFact): Activity? {
    return when(fact.attribute){
        CoreAttribute.Uri.spec -> Actions(true)
        CoreAttribute.Trust.spec -> Actions(false, CoreAttribute.Trust.spec.codec.decode(fact.value.bytes) as Float, null,null)
        CoreAttribute.Like.spec -> Actions(false, null, CoreAttribute.Like.spec.codec.decode(fact.value.bytes) as Boolean, null)
        CoreAttribute.Rating.spec -> Actions(false, null, null, CoreAttribute.Rating.spec.codec.decode(fact.value.bytes) as Float)
        else -> null
    }.nullOrElse { Activity(authorityId, name, epochSecond, it) } // TODO: Set epoch
}

fun getFact(facts: List<TransactionFact>, attribute: Attribute): TransactionFact? {
    return facts.firstOrNull{ it.attribute == attribute }
}

fun getAttributeValue(facts: List<TransactionFact>, attribute: Attribute): Any? {
    return getFact(facts, attribute).nullOrElse { attribute.codec.decode(it.value.bytes) }
}

fun getActivity(authorityId: Id, name: String, epochSecond: Long, save: Boolean?, trust: Float?, like: Boolean?, rating: Float?): Activity? {
    return if(listOf(save, trust, like, rating).all { it == null })
        null
    else
        Activity(authorityId, name, epochSecond, Actions(save, trust, like, rating))
}

fun getActivity(authorityId: Id, name: String, epochSecond: Long, facts: List<TransactionFact>): Activity? {
    return getActivity(
        authorityId,
        name,
        epochSecond,
        getFact(facts, CoreAttribute.Uri.spec).nullOrElse { true },
        getAttributeValue(facts, CoreAttribute.Trust.spec) as Float?,
        getAttributeValue(facts, CoreAttribute.Like.spec) as Boolean?,
        getAttributeValue(facts, CoreAttribute.Rating.spec) as Float?
    )
}

fun getActorName(id: Id, rootAuthority: Authority, peerRouter: PeerRouter): String {
    return if (id == rootAuthority.authorityId)
        "You"
    else
        peerRouter.getPeer(id)?.name ?: "Unknown"


}

fun getEntityActivities(transactions: Iterable<Transaction>, idToName: (Id) -> String): Map<Id, List<Activity>> {
    return transactions.flatMap { transaction ->
        transaction.transactionEntities
            .map { Pair(it.entityId, getActivity(transaction.authorityId, idToName(transaction.authorityId), transaction.epochSecond, it.facts)) }
            .filter { it.second != null }
            .map { Pair(it.first, it.second!!) }
    }
        .groupBy { it.first }
        .entries.associate { entry -> Pair(entry.key, entry.value.map { it.second }) }
}

suspend fun handleGetFeed(call: ApplicationCall, authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter) {
    // TODO: Look for startTransactionId in call (For paging)
    val signedTransactions = entityStore.getSignedTransactions(emptyList(), null, EntityStore.TransactionOrder.Descending, 100) // TODO: Config limit
    val entityIds = signedTransactions.flatMap { tx -> tx.transaction.transactionEntities.map { it.entityId } }.distinct()
    val entityFacts = entityStore.getFacts(emptyList(), entityIds)
        .groupBy { it.entityId }
        .filter{ Entity.getInstance(it.value) !is DataEntity }
        .toMap()

    val idToName: (Id) -> String = { id -> getActorName(id, authority, peerRouter) }
    val entityActivities = getEntityActivities(signedTransactions.map { it.transaction }, idToName)

    call.respond(FeedResult(
        signedTransactions.lastOrNull()?.transaction?.id,
        entityIds
            .filter { entityFacts.containsKey(it) }
            .map{
            val entityIdFacts = entityFacts[it] ?: throw RuntimeException("Can't find facts for id: $it")
            val entityIdActivities = entityActivities[it] ?: throw RuntimeException("Can't find activities for id: $it")

            EntityResult(it, getSummary(entityIdFacts), entityIdActivities) }
    ))
}