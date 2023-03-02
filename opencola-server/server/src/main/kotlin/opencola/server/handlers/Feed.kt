package opencola.server.handlers

import io.opencola.model.*
import kotlinx.serialization.Serializable
import io.opencola.util.nullOrElse
import io.opencola.model.CoreAttribute.*
import io.opencola.search.SearchIndex
import io.opencola.storage.AddressBook
import io.opencola.storage.AddressBookEntry
import io.opencola.storage.EntityStore
import io.opencola.storage.PersonaAddressBookEntry
import opencola.server.handlers.EntityResult.*

@Serializable
data class FeedResult(val pagingToken: String?, val results: List<EntityResult>)

fun entityAttributeAsString(entity: Entity, attribute: Attribute): String? {
    return entity.getValue(attribute.name).nullOrElse { attribute.codec.decode(it.bytes).toString() }
}

fun getPostedById(entities: List<Entity>): Id {
    return entities.minByOrNull {
        it.getAllFacts().minByOrNull { fact -> fact.epochSecond!! }!!.epochSecond!!
    }!!.authorityId
}

fun getSummary(personaIds: Set<Id>, entities: List<Entity>, idToAuthority: (Id) -> AddressBookEntry?): Summary {
    val entity = entities.minByOrNull { if (personaIds.contains(it.authorityId)) 0 else 1 }!!
    val postedByAuthority = idToAuthority(getPostedById(entities))

    return Summary(
        entityAttributeAsString(entity, Name.spec),
        entityAttributeAsString(entity, Uri.spec),
        entityAttributeAsString(entity, Description.spec),
        entityAttributeAsString(entity, ImageUri.spec),
        postedByAuthority?.name,
        postedByAuthority?.imageUri?.toString()
    )
}

fun factToAction(comments: Map<Id, CommentEntity>, fact: Fact): Action? {
    return when (fact.attribute) {
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

fun factsToActions(comments: Map<Id, CommentEntity>, facts: List<Fact>): List<Action> {
    val factsByAttribute = facts.groupBy { it.attribute }
    val dataIdPresent = factsByAttribute[DataId.spec] != null

    return factsByAttribute.flatMap { (attribute, facts) ->
        if (dataIdPresent && attribute == Type.spec) {
            // An item is "saved" when either it is created (new type property) or archived (new dataId)
            // When a dataId is present, don't double count the type property
            emptyList()
        } else
            facts.mapNotNull { factToAction(comments, it) }
    }
}

fun entityActivities(addressBookEntry: AddressBookEntry, entity: Entity, comments: Map<Id, CommentEntity>): List<Activity> {
    if (addressBookEntry.entityId != entity.authorityId) {
        throw IllegalArgumentException("authorityId does not match entity")
    }

    return entity.getCurrentFacts()
        .groupBy { it.transactionOrdinal }
        .map { (_, facts) ->
            Activity(
                addressBookEntry,
                facts.first().epochSecond!!,
                factsToActions(comments, facts)
            )
        }
}

fun activitiesByEntityId(
    idToAuthority: (Id) -> AddressBookEntry?,
    entities: Iterable<Entity>,
    comments: Map<Id, CommentEntity>
): Map<Id, List<Activity>> {
    return entities
        .groupBy { it.entityId }
        .map { (entityId, entities) ->
            val activities = entities
                .mapNotNull { entity ->
                    idToAuthority(entity.authorityId).nullOrElse {
                        entityActivities(
                            it,
                            entity,
                            comments
                        )
                    }
                }
                .flatten()
                .filter { it.actions.isNotEmpty() }
                .sortedByDescending { it.epochSecond }
            Pair(entityId, activities)
        }
        .associate { it }
}

fun isEntityIsVisible(entity: Entity): Boolean {
    return when (entity) {
        is ResourceEntity -> true
        is PostEntity -> true
        else -> false
    }
}

fun getEntityIds(entityStore: EntityStore, authorityIds: Set<Id>, searchIndex: SearchIndex, query: String?): Set<Id> {
    // TODO: This will generally result in an unpredictable number of entities, as single actions (like, comment, etc.)
    //  take a transaction. Fix this by requesting transaction batches until no more or 100 entities have been reached
    val entityIds = if (query == null || query.trim().isEmpty()) {
        val signedTransactions = entityStore.getSignedTransactions(
            authorityIds,
            null,
            EntityStore.TransactionOrder.TimeDescending,
            100
        ) // TODO: Config limit
        signedTransactions.flatMap { tx -> tx.transaction.transactionEntities.map { it.entityId } }
    } else {
        // TODO: Get add peers of personas to id list
        searchIndex.search(authorityIds, query).map { it.entityId }
    }

    return entityIds.toSet()
}

fun getComments(entityStore: EntityStore, entities: Iterable<Entity>): Map<Id, CommentEntity> {
    val commentIds = entities
        .flatMap { it.commentIds }
        .toSet()

    if (commentIds.isEmpty()) {
        return emptyMap()
    }

    return entityStore.getEntities(emptySet(), commentIds)
        .mapNotNull { it as? CommentEntity }
        .associateBy { it.entityId }
}

fun getAddressBookMap(addressBook: AddressBook): Map<Id, AddressBookEntry> {
    return addressBook.getEntries().map {
        if(it is PersonaAddressBookEntry)
            AddressBookEntry(it.personaId, it.entityId, "You (${it.name})", it.publicKey, it.address, it.imageUri, it.isActive)
        else
            it
    }.associateBy { it.entityId }
}

fun getPersonaId(addressBook: AddressBook,  activities: List<Activity>) : Id {
    val authorities = addressBook.getEntries().filter { it.isActive }
    return activities.maxByOrNull { it.epochSecond }?.let { activity ->
        authorities.firstOrNull { it.entityId.toString() == activity.authorityId }?.personaId
    } ?: authorities.first { it is PersonaAddressBookEntry }.personaId
}

fun getEntityResults(
    personaIds: Set<Id>,
    entityStore: EntityStore,
    addressBook: AddressBook,
    entityIds: Set<Id>, // TODO: Could have emptySet() as default here
): List<EntityResult> {
    if (entityIds.isEmpty()) {
        return emptyList()
    }

    val authorityIds = addressBook.getEntries().filter { it.personaId in personaIds }.map { it.entityId }.toSet()
    val addressBookMap = getAddressBookMap(addressBook)
    val idToAuthority: (Id) -> AddressBookEntry? = { id -> addressBookMap[id] }
    val entities = entityStore.getEntities(authorityIds, entityIds).filter { isEntityIsVisible(it) }
    val comments = getComments(entityStore, entities)
    val entitiesByEntityId = entities.groupBy { it.entityId }
    val activitiesByEntityId = activitiesByEntityId(idToAuthority, entities, comments)

    return entityIds
        .filter { entitiesByEntityId.containsKey(it) }
        .map {
            val entitiesForId = entitiesByEntityId[it]!!
            val activities = activitiesByEntityId[it]!!
            EntityResult(
                it,
                getPersonaId(addressBook, activities),
                getSummary(personaIds, entitiesForId, idToAuthority),
                activities
            )
        }
}

fun getEntityResult(
    personaId: Id,
    entityStore: EntityStore,
    addressBook: AddressBook,
    entityId: Id
): EntityResult? {
    return getEntityResults(setOf(personaId), entityStore, addressBook, setOf(entityId)).firstOrNull()
}

fun handleGetFeed(
    personaIds: Set<Id>,
    entityStore: EntityStore,
    searchIndex: SearchIndex,
    addressBook: AddressBook,
    query: String?
): FeedResult {
    // TODO: This could be tightened up. We're accessing the address book multiple times. Could pass entries around instead
    val authorityIds = addressBook.getEntries().filter { it.personaId in personaIds }.map { it.entityId }.toSet()
    val entityIds = getEntityIds(entityStore, authorityIds, searchIndex, query)
    return FeedResult(null, getEntityResults(authorityIds, entityStore, addressBook, entityIds))
}