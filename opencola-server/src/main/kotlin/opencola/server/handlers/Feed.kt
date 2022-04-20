package opencola.server.handlers

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.service.EntityResult
import java.net.URI

@Serializable
data class FeedResult(val pagingToken: String?, val results: List<EntityResult>) {
    constructor(transactionId: Id?, results: List<EntityResult>) : this(transactionId?.toString(), results)
}

fun stringAttributeFromFacts(facts: List<Fact>, attribute: Attribute): String? {
    return getFact(facts, attribute).nullOrElse { attribute.codec.decode(it.value.bytes).toString() }
}

fun getSummary(facts: List<Fact>): EntityResult.Summary {
    return EntityResult.Summary(
        stringAttributeFromFacts(facts, CoreAttribute.Name.spec),
        stringAttributeFromFacts(facts, CoreAttribute.Uri.spec)!!,
        stringAttributeFromFacts(facts, CoreAttribute.Description.spec),
        stringAttributeFromFacts(facts, CoreAttribute.ImageUri.spec)
    )
}

fun getActivity(authority: Authority, dataId: Id?, epochSecond: Long, save: Boolean?, trust: Float?, like: Boolean?, rating: Float?
): EntityResult.Activity? {
    return if (listOf(save, trust, like, rating).all { it == null })
        null
    else
        EntityResult.Activity(authority, dataId, epochSecond, Actions(save, trust, like, rating))
}

// TODO: generate this list once, vs creating Authorities for each activity
fun getAuthority(id: Id, rootAuthority: Authority, peerRouter: PeerRouter): Authority? {
    return if (id == rootAuthority.authorityId)
        Authority(rootAuthority.publicKey!!, URI(""), name = "You")
    else
        peerRouter.getPeer(id).nullOrElse { Authority(it.publicKey, URI("http://${it.host}"), name = it.name) }
}

fun getFact(facts: Iterable<Fact>, attribute: Attribute): Fact? {
    return facts
        .filter { it.operation != Operation.Retract }
        .lastOrNull { it.attribute == attribute }
}

fun getAttributeValueFromFact(facts: Iterable<Fact>, attribute: Attribute): Any? {
    return getFact(facts, attribute).nullOrElse { attribute.codec.decode(it.value.bytes) }
}

fun getDataId(authorityId: Id, facts: List<Fact>) : Id?{
    val dataIdAttribute = CoreAttribute.DataId.spec

    return facts
        .filter { it.authorityId == authorityId && it.operation != Operation.Retract }
        .lastOrNull { it.attribute == dataIdAttribute }
        .nullOrElse { dataIdAttribute.codec.decode(it.value.bytes) as Id }
}

fun getActivityFromFacts(authority: Authority, facts: Iterable<Fact>): EntityResult.Activity? {
    return getActivity(
        authority,
        getFact(facts, CoreAttribute.DataId.spec)?.value?.bytes.nullOrElse { Id.decode(it) },
        facts.first().epochSecond!!,
        getFact(facts, CoreAttribute.Uri.spec).nullOrElse { true },
        getAttributeValueFromFact(facts, CoreAttribute.Trust.spec) as Float?,
        getAttributeValueFromFact(facts, CoreAttribute.Like.spec) as Boolean?,
        getAttributeValueFromFact(facts, CoreAttribute.Rating.spec) as Float?
    )
}

fun getEntityActivitiesFromFacts(entityFacts: Iterable<Fact>, idToAuthority: (Id) -> Authority?): List<EntityResult.Activity> {
    if(entityFacts.distinctBy { it.entityId }.size != 1){
        throw IllegalArgumentException("Attempt to get activities from facts with multiple entities")
    }

    return entityFacts
        .sortedByDescending { it.epochSecond }
        .groupBy { Pair(it.authorityId, it.transactionOrdinal) }
        .map {
            val (authorityId, epochSecond) = it.key
            idToAuthority(authorityId).nullOrElse { authority -> getActivityFromFacts(authority, it.value) }
        }.filterNotNull()
}

// TODO - All items should be visible in search (i.e. even un-liked)
// TODO - Add unit tests for items with multiple authorities and make sure remote authority items are returned
fun isEntityIsVisible(authorityId: Id, facts: Iterable<Fact>) : Boolean {
    val authorityToFacts = facts.groupBy { it.authorityId }
    val authorityFacts = authorityToFacts[authorityId] ?: authorityToFacts.values.firstOrNull() ?: return false

    return when(val entity = Entity.fromFacts(authorityFacts)){
        is ResourceEntity -> entity.authorityId != authorityId || entity.like != false
        is ActorEntity -> entity.authorityId != authorityId || entity.like != false
        else -> false
    }
}

fun getEntityFacts(entityStore: EntityStore, authorityId: Id, entityIds: Iterable<Id>): Map<Id, List<Fact>> {
    return entityStore.getFacts(emptyList(), entityIds)
        .groupBy { it.entityId }
        .filter { isEntityIsVisible(authorityId, it.value) }
        .toMap()
}

fun getEntityIds(entityStore: EntityStore, searchIndex: SearchIndex, query: String?): List<Id> {
    return if (query == null || query.trim().isEmpty()){
        val signedTransactions = entityStore.getSignedTransactions(emptyList(),null, EntityStore.TransactionOrder.Descending,100) // TODO: Config limit
        signedTransactions.flatMap { tx -> tx.transaction.transactionEntities.map { it.entityId } }.distinct()
    } else {
        searchIndex.search(query).map { it.entityId }
    }
}

suspend fun handleGetFeed(
    call: ApplicationCall,
    authority: Authority,
    entityStore: EntityStore,
    searchIndex: SearchIndex,
    peerRouter: PeerRouter
) {
    // TODO: Look for startTransactionId in call (For paging)
    val entityIds = getEntityIds(entityStore, searchIndex, call.parameters["q"])
    val entityFactsById = getEntityFacts(entityStore, authority.authorityId, entityIds)
    val idToAuthority: (Id) -> Authority? = { id -> getAuthority(id, authority, peerRouter) }
    val entityActivitiesById =
        entityFactsById.entries.associate { Pair(it.key, getEntityActivitiesFromFacts(it.value, idToAuthority)) }

    call.respond(FeedResult(
        "",
        entityIds
            .filter { entityFactsById.containsKey(it) }
            .map {
                val entityFacts = entityFactsById[it]!!
                EntityResult(
                    it,
                    getDataId(authority.authorityId, entityFacts),
                    getSummary(entityFacts),
                    entityActivitiesById[it]!!
                )
            }
    ))
}