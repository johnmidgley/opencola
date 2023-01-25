package opencola.server.handlers

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.opencola.model.*
import kotlinx.serialization.Serializable
import io.opencola.util.nullOrElse
import io.opencola.model.CoreAttribute.*
import io.opencola.search.SearchIndex
import io.opencola.storage.AddressBook
import io.opencola.storage.EntityStore
import opencola.server.handlers.EntityResult.*

@Serializable
data class FeedResult(val authorityId: String, val pagingToken: String?, val results: List<EntityResult>) {
    constructor(authorityId: Id, transactionId: Id?, results: List<EntityResult>) : this(authorityId.toString(), transactionId?.toString(), results)
}

fun entityAttributeAsString(entity: Entity, attribute: Attribute) : String? {
    return entity.getValue(attribute.name).nullOrElse { attribute.codec.decode(it.bytes).toString() }
}

fun getPostedById(entities: List<Entity>) : Id {
    return entities.minByOrNull {
        it.getAllFacts().minByOrNull {
                fact -> fact.epochSecond!! }!!.epochSecond!!
    } !!.authorityId
}

fun getSummary(authorityId: Id, entities: List<Entity>, idToAuthority: (Id) -> Authority?): Summary {
    val entity = entities.minByOrNull { if (it.authorityId == authorityId) 0 else 1 } !!
    val postedByAuthority = idToAuthority(getPostedById(entities))

    return Summary(
        entityAttributeAsString(entity, Name.spec),
        entityAttributeAsString(entity, Uri.spec),
        entityAttributeAsString(entity, Description.spec),
        entityAttributeAsString(entity, ImageUri.spec),
        if (postedByAuthority?.entityId == authorityId) "You" else postedByAuthority?.name,
        postedByAuthority?.imageUri?.toString()
    )
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
    val dataIdPresent = factsByAttribute[DataId.spec] != null

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
    if (authority.entityId != entity.authorityId) {
        throw IllegalArgumentException("authorityId does not match entity")
    }

    return entity.getCurrentFacts()
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
fun isEntityIsVisible(authorityId: Id, entity: Entity) : Boolean {
    return when(entity){
        // TODO: This hides unliked entities in search results
        is ResourceEntity -> entity.authorityId != authorityId || entity.like != false
        is PostEntity -> entity.authorityId != authorityId || entity.like != false
        else -> false
    }
}

fun getEntityIds(entityStore: EntityStore, searchIndex: SearchIndex, query: String?): Set<Id> {
    // TODO: This will generally result in an unpredictable number of entities, as single actions (like, comment, etc.)
    //  take a transaction. Fix this by requesting transaction batches until no more or 100 entities have been reached
    val entityIds =  if (query == null || query.trim().isEmpty()){
        val signedTransactions = entityStore.getSignedTransactions(emptyList(),null, EntityStore.TransactionOrder.TimeDescending,100) // TODO: Config limit
        signedTransactions.flatMap { tx -> tx.transaction.transactionEntities.map { it.entityId } }
    } else {
        searchIndex.search(query).map { it.entityId }
    }

    return entityIds.toSet()
}

fun getComments(entityStore: EntityStore, entities: Iterable<Entity>): Map<Id, CommentEntity> {
    val commentIds = entities
        .flatMap { it.commentIds }
        .toSet()

    if(commentIds.isEmpty()){
        return emptyMap()
    }

   return entityStore.getEntities(emptySet(), commentIds)
       .mapNotNull { it as? CommentEntity }
       .associateBy { it.entityId }
}

fun getAuthority(personaId: Id, addressBook: AddressBook, authorityId: Id): Authority? {
    return addressBook.getAuthority(personaId, authorityId).nullOrElse {
        it.also {
            if (it.entityId == personaId) {
                it.name = "You"
            }
        }
    }
}

fun getEntityResults(authority: Authority, entityStore: EntityStore, addressBook: AddressBook, entityIds: Set<Id>): List<EntityResult> {
    if(entityIds.isEmpty()){
        return emptyList()
    }

    val idToAuthority: (Id) -> Authority? = { id -> getAuthority(authority.entityId, addressBook, id) }
    val entities = entityStore.getEntities(emptySet(), entityIds).filter { isEntityIsVisible(authority.authorityId, it) }
    val comments = getComments(entityStore, entities)
    val entitiesByEntityId = entities.groupBy { it.entityId }
    val activitiesByEntityId = activitiesByEntityId(idToAuthority, entities, comments)

    return entityIds
            .filter { entitiesByEntityId.containsKey(it) }
            .map {
                val entitiesForId = entitiesByEntityId[it]!!
                EntityResult(
                    it,
                    getSummary(authority.authorityId, entitiesForId, idToAuthority),
                    activitiesByEntityId[it]!!
                )
            }
}

fun getEntityResult(authority: Authority, entityStore: EntityStore, addressBook: AddressBook, entityId: Id) : EntityResult? {
    return getEntityResults(authority, entityStore, addressBook, setOf(entityId)).firstOrNull()
}

suspend fun handleGetFeed(
    call: ApplicationCall,
    authority: Authority,
    entityStore: EntityStore,
    searchIndex: SearchIndex,
    addressBook: AddressBook,
) {
    // TODO: Look for startTransactionId in call (For paging)
    val entityIds = getEntityIds(entityStore, searchIndex, call.parameters["q"])

    call.respond(FeedResult(
        authority.authorityId,
        null,
        getEntityResults(authority, entityStore, addressBook, entityIds)
    ))
}