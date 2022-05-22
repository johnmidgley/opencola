package opencola.server.handlers

import io.ktor.application.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.model.CoreAttribute.*
import opencola.core.network.PeerRouter
import opencola.core.search.SearchIndex
import opencola.core.storage.EntityStore
import opencola.service.EntityResult
import opencola.service.EntityResult.*
import java.net.URI

@Serializable
data class FeedResult(val authorityId: String, val pagingToken: String?, val results: List<EntityResult>) {
    constructor(authorityId: Id, transactionId: Id?, results: List<EntityResult>) : this(authorityId.toString(), transactionId?.toString(), results)
}

fun entityAttributeAsString(entity: Entity, attribute: Attribute) : String? {
    return entity.getValue(attribute.name).nullOrElse { attribute.codec.decode(it.bytes).toString() }
}

fun getSummary(authorityId: Id, entities: List<Entity>): Summary {
    val entity = entities.minByOrNull { if (it.authorityId == authorityId) 0 else 1 } !!

    return Summary(
        entityAttributeAsString(entity, Name.spec),
        entityAttributeAsString(entity, Uri.spec)!!,
        entityAttributeAsString(entity, Description.spec),
        entityAttributeAsString(entity, ImageUri.spec),
    )
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
    val dataIdAttribute = DataId.spec

    return facts
        .filter { it.authorityId == authorityId && it.operation != Operation.Retract }
        .lastOrNull { it.attribute == dataIdAttribute }
        .nullOrElse { dataIdAttribute.codec.decode(it.value.bytes) as Id }
}

fun factToAction(comments: Map<Id, CommentEntity>, fact: Fact) : Action? {
    return when(fact.attribute) {
        Type.spec -> Action(ActionType.Save, null, null)
        DataId.spec -> Action(ActionType.Save, fact.decodeValue(), null)
        Trust.spec -> Action(ActionType.Trust, null, fact.decodeValue())
        Like.spec -> Action(ActionType.Like, null, fact.decodeValue())
        Rating.spec -> Action(ActionType.Rate, null, fact.decodeValue())
        Tags.spec -> Action(ActionType.Tag, null, fact.decodeValue())
        CommentIds.spec -> {
            val commentId = fact.decodeValue<Id>()
            Action(ActionType.Comment, commentId, comments.getValue(commentId).text)
        }
        else -> null
    }
}

fun factsToActions(comments: Map<Id, CommentEntity>, facts: List<Fact>) : List<Action> {
    val factsByAttribute = facts.groupBy { it.attribute }
    val dataIdPresent = factsByAttribute[CoreAttribute.DataId.spec] != null

    return factsByAttribute.flatMap { (attribute, facts) ->
        if(dataIdPresent && attribute == Type.spec) {
            // An item is "saved" when either it is created (new type property) or archived (new dataId)
            // When a dataId is present, don't double count the type property
            emptyList()
        }
        else
            facts.mapNotNull { factToAction(comments, it) }
    }
}

fun entityActivities(authority: Authority, entity: Entity, comments: Map<Id, CommentEntity>): List<Activity> {
    if (authority.authorityId != entity.authorityId) {
        throw IllegalArgumentException("authorityId does not match entity")
    }

    return entity.getNonRetractedFacts()
        .groupBy { it.transactionOrdinal }
        .map { (_, facts) ->
            Activity(
                authority,
                facts.first().epochSecond!!,
                factsToActions(comments, facts)
            )
        }
}

fun activitiesByEntityId(idToAuthority: (Id) -> Authority?,
                         entities: Iterable<Entity>,
                         comments: Map<Id, CommentEntity>): Map<Id, List<Activity>> {
    return entities
        .groupBy { it.entityId }
        .map { (entityId, entities) ->
            val activities = entities
                .mapNotNull { entity -> idToAuthority(entity.authorityId).nullOrElse { entityActivities(it, entity, comments) } }
                .flatten()
                .filter { it.actions.isNotEmpty() }
                .sortedByDescending { it.epochSecond }
            Pair(entityId, activities)
        }
        .associate { it }
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

fun isEntityIsVisible(authorityId: Id, entity: Entity) : Boolean {
    return when(entity){
        // TODO: This hides unliked entities in search results
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
    // TODO: This will generally result in an unpredictable number of entities, as single actions (lile, comment, etc.)
    //  take a transaction. Fix this by requesting transaction batches until no more or 100 entities have been reached
    val entityIds =  if (query == null || query.trim().isEmpty()){
        val signedTransactions = entityStore.getSignedTransactions(emptyList(),null, EntityStore.TransactionOrder.TimeDescending,100) // TODO: Config limit
        signedTransactions.flatMap { tx -> tx.transaction.transactionEntities.map { it.entityId } }
    } else {
        searchIndex.search(query).map { it.entityId }
    }

    return entityIds.distinct()
}

fun getComments(entityStore: EntityStore, entities: Iterable<Entity>): Map<Id, CommentEntity> {
    val commentIds = entities
        .mapNotNull { it as? ResourceEntity }
        .flatMap { it.commentIds }
        .toSet()

   return entityStore.getEntities(emptyList(), commentIds)
       .mapNotNull { it as? CommentEntity }
       .associateBy { it.entityId }
}

fun getEntityResults(authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter, entityIds: Iterable<Id>): List<EntityResult> {
    val idToAuthority: (Id) -> Authority? = { id -> getAuthority(id, authority, peerRouter) }
    val entities = entityStore.getEntities(emptyList(), entityIds).filter { isEntityIsVisible(authority.authorityId, it) }
    val comments = getComments(entityStore, entities)
    val entitiesByEntityId = entities.groupBy { it.entityId }
    val activitiesByEntityId = activitiesByEntityId(idToAuthority, entities, comments)

    return entityIds
            .filter { entitiesByEntityId.containsKey(it) }
            .map {
                val entitiesForId = entitiesByEntityId[it]!!
                EntityResult(
                    it,
                    getSummary(authority.authorityId, entitiesForId),
                    activitiesByEntityId[it]!!
                )
            }
}

fun getEntityResult(authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter, entityId: Id) : EntityResult? {
    return getEntityResults(authority, entityStore, peerRouter, listOf(entityId)).firstOrNull()
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

    call.respond(FeedResult(
        authority.authorityId,
        null,
        getEntityResults(authority, entityStore, peerRouter, entityIds)
    ))
}