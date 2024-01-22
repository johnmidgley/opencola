package opencola.server.handlers

import io.opencola.application.Application
import io.opencola.event.bus.EventBus
import io.opencola.event.bus.Events
import io.opencola.model.*
import kotlinx.serialization.Serializable
import io.opencola.model.CoreAttribute.*
import io.opencola.search.Query
import io.opencola.search.QueryParser
import io.opencola.search.SearchIndex
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.filestore.ContentAddressedFileStore
import mu.KotlinLogging
import opencola.server.handlers.EntityResult.*

private val logger = KotlinLogging.logger("Feed")

@Serializable
data class FeedResult internal constructor(
    val context: String?,
    val pagingToken: String?,
    val results: List<EntityResult>
) {
    constructor(context: Context, pagingToken: String?, results: List<EntityResult>) : this(
        context.toString(),
        pagingToken,
        results
    )
}

fun entityAttributeAsString(entity: Entity, attribute: Attribute): String? {
    return entity.getValue(attribute.name)?.get()?.toString()
}

fun getPostedById(entities: List<Entity>): Id {
    return entities.minByOrNull {
        it.getAllFacts().minByOrNull { fact -> fact.epochSecond!! }!!.epochSecond!!
    }!!.authorityId
}

fun getOriginDistance(entities: Iterable<Entity>): Int? {
    return entities.minOf { it.originDistance ?: 0 }.let { if (it == 0) null else it }
}

fun getSummary(entities: List<Entity>, authoritiesById: Map<Id, AddressBookEntry>): Summary {
    val entity = entities
        .filter { it !is RawEntity } // Raw entities cannot be displayed - they just contain activity facts
        .maxBy { e -> e.getCurrentFacts().maxOf { it.transactionOrdinal!! } }
    val postedByAuthority = authoritiesById[getPostedById(entities)]

    return Summary(
        entityAttributeAsString(entity, Name.spec),
        entityAttributeAsString(entity, Uri.spec),
        entityAttributeAsString(entity, Description.spec),
        entityAttributeAsString(entity, ImageUri.spec),
        getOriginDistance(entities),
        postedByAuthority?.let { EntityResult.Authority(postedByAuthority) }
    )
}

fun factToAction(children: Children, fact: Fact): Action? {
    return when (fact.attribute) {
        Type.spec -> Action(ActionType.Save, null, null)
        DataIds.spec -> Action(ActionType.Save, fact.unwrapValue(), null)
        Trust.spec -> Action(ActionType.Trust, null, fact.unwrapValue())
        Like.spec -> Action(ActionType.Like, null, fact.unwrapValue())
        Rating.spec -> Action(ActionType.Rate, null, fact.unwrapValue())
        Tags.spec -> Action(ActionType.Tag, null, fact.unwrapValue())
        CommentIds.spec -> {
            val commentId = fact.unwrapValue<Id>()
            // A comment can be missing if the entity is a RawEntity referring to a comment that was deleted
            children.comments[commentId]?.let {
                val parentId = if (it.topLevelParentId != null) it.parentId else null
                Action(ActionType.Comment, commentId, it.text, parentId)
            }
        }

        AttachmentIds.spec -> {
            val attachmentId = fact.unwrapValue<Id>()
            val attachment = children.attachments[attachmentId]

            if (attachment == null) {
                logger.warn { "Attachment $attachmentId not found" }
                null
            } else
                Action(ActionType.Attach, attachmentId, attachment.name)
        }

        else -> null
    }
}

fun factsToActions(
    children: Children,
    facts: List<Fact>
): List<Action> {
    val factsByAttribute = facts.groupBy { it.attribute }
    val dataIdPresent = factsByAttribute[DataIds.spec] != null

    return factsByAttribute.flatMap { (attribute, facts) ->
        if (dataIdPresent && attribute == Type.spec) {
            // An item is "saved" when either it is created (new type property) or archived (new dataId)
            // When a dataId is present, don't double count the type property
            emptyList()
        } else
            facts.mapNotNull { factToAction(children, it) }
    }
}

fun entityActivities(
    addressBookEntry: AddressBookEntry,
    entity: Entity,
    children: Children,
): List<Activity> {
    if (addressBookEntry.entityId != entity.authorityId) {
        throw IllegalArgumentException("authorityId does not match entity")
    }

    return entity.getCurrentFacts()
        .groupBy { it.transactionOrdinal }
        .map { (_, facts) ->
            Activity(
                addressBookEntry,
                facts.first().epochSecond!!,
                factsToActions(children, facts)
            )
        }
}

fun activitiesByEntityId(
    authoritiesById: Map<Id, AddressBookEntry>,
    entities: Iterable<Entity>,
    children: Children,
): Map<Id, List<Activity>> {
    return entities
        .groupBy { it.entityId }
        .map { (entityId, entities) ->
            val activities = entities
                .mapNotNull { entity ->
                    authoritiesById[entity.authorityId]?.let {
                        entityActivities(
                            it,
                            entity,
                            children,
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
        is RawEntity -> true
        else -> false
    }
}

data class PageableEntityIds(val entityIds: Set<Id>, val pagingToken: String?)

fun getEntityIdsByTransactionActivity(
    entityStore: EntityStore,
    authorityIds: Set<Id>,
    pagingToken: String?
): Sequence<Id> = sequence {
    // At a high level, the feed is ordered in reverse chronological order of activity on entities. We have 3
    // basic choices in how we define activity:
    //
    // 1. Order by ANY activity, including both additions and retractions. This implies that unliking an item
    // that is far down in the feed will bump it to the top, which is a bit surprising, and since activity is
    // removed, it is not visible to the viewer.
    //
    // 2. Order by any visible activity, in particular, activity returned in the feed item that is accessible to
    // the viewer of the feed. This implies that liking an item bumps it to the top of the feed, while unliking
    // the item would return it to the position it was in before being liked. While somewhat intuitive,
    // this means that an item will like "disappear" after a refresh of the page, which is unexpected.
    //
    // 3. Order by any ADD activity, whether or not it is visible. This implies that liking an item bumps it
    // to the top of the feed, but unliking it leaves it there. This seems the most intuitive to the user,
    // and so is how we do things below.
    val signedTransactionSequence = entityStore
        .getAllSignedTransactions(authorityIds, EntityStore.TransactionOrder.IdDescending)
        .filter { s -> s.transaction.transactionEntities.any { e -> e.facts.any { it.operation == Operation.Add } } }

    fun SignedTransaction.entityIds(): Set<Id> = transaction.transactionEntities.map { it.entityId }.toSet()
    val pagingId = pagingToken?.let { Id.decode(pagingToken) }
    val skippedEntityIds = mutableSetOf<Id>()
    val iterator = signedTransactionSequence.iterator()

    // Skip any ids up to, and including, the paging id
    if (pagingId != null) {
        var foundPagingId = false
        while (!foundPagingId && iterator.hasNext()) {
            for (entityId in iterator.next().entityIds()) {
                skippedEntityIds.add(entityId)
                if (entityId == pagingId) {
                    foundPagingId = true
                    break
                }
            }
        }
    }

    iterator
        .asSequence()
        .flatMap { it.entityIds() }
        .filter { !skippedEntityIds.contains(it) }
        .distinct()
        .filter { id ->
            entityStore.getEntities(authorityIds, setOf(id)).let { it.isNotEmpty() && isEntityIsVisible(it.first()) }
        }
        .forEach { yield(it) }
}

fun getEntityIds(
    entityStore: EntityStore,
    searchIndex: SearchIndex,
    query: Query,
    limit: Int,
    pagingToken: String? = null
): PageableEntityIds {
    val authorityOnlyQuery = query.tags.isEmpty() && query.terms.isEmpty()
    if (authorityOnlyQuery) {
        val entityIds =
            getEntityIdsByTransactionActivity(entityStore, query.authorityIds, pagingToken).take(limit).toList()
        val resultPagingToken = if(entityIds.size < limit) null else entityIds.lastOrNull()?.toString()
        return PageableEntityIds(entityIds.toSet(), resultPagingToken)
    } else {
        val searchResults = searchIndex.getResults(query, limit, pagingToken)
        return PageableEntityIds(searchResults.items.map { it.entityId }.toSet(), searchResults.pagingToken)
    }
}

fun getChildIds(entities: Iterable<Entity>, childSelector: (Entity) -> Iterable<Id>): Set<Id> {
    return entities.flatMap { childSelector(it) }.toSet()
}

data class Children(val comments: Map<Id, CommentEntity>, val attachments: Map<Id, DataEntity>)

fun getChildren(
    authorityIds: Set<Id>,
    entityStore: EntityStore,
    entities: Iterable<Entity>,
): Children {
    val ids = getChildIds(entities) { it.commentIds.plus(it.attachmentIds).toSet() }

    if (ids.isEmpty()) {
        return Children(emptyMap(), emptyMap())
    }

    val children = entityStore.getEntities(authorityIds, ids)
    // "RawEntity"s can't be displayed on their own, so don't include them unless a full version of the entity is present
    val entityIds = entities.filter { it !is RawEntity }.map { it.entityId }.toSet()
    val allChildren = children.filter { it !is RawEntity }.map { it.entityId }.toSet()
    val allEntityIds = entityIds.plus(allChildren)

    val comments = children
        .filterIsInstance<CommentEntity>()
        .filter { it.parentId in allEntityIds }
        .associateBy { it.entityId }

    // TODO: Attachments are not necessarily unique. Should select best one
    val attachments = children.filterIsInstance<DataEntity>().associateBy { it.entityId }

    return Children(comments, attachments)
}

fun getAuthoritiesById(addressBook: AddressBook, personaIds: Set<Id>): Map<Id, AddressBookEntry> {
    return addressBook.getEntries()
        .filter { it.personaId in personaIds }
        .associateBy { it.entityId }
}

fun getPersonaId(addressBook: AddressBook, activities: List<Activity>): Id {
    val authorities = addressBook.getEntries()
    return activities.maxByOrNull { it.epochSecond }?.let { activity ->
        authorities.firstOrNull { it.entityId.toString() == activity.authorityId }?.personaId
    } ?: authorities.first { it is PersonaAddressBookEntry }.personaId
}

fun requestMissingAttachmentIds(
    fileStore: ContentAddressedFileStore,
    eventBus: EventBus,
    entities: Iterable<Entity>,
) {
    val allAttachmentIds = getChildIds(entities) { it.attachmentIds.toSet() }
    val missingAttachmentIds = allAttachmentIds.filter { !fileStore.exists(it) }
    for (id in missingAttachmentIds) {
        logger.info { "Requesting missing attachment $id" }
        eventBus.sendMessage(Events.DataMissing.toString(), id.encodeProto())
    }
}

fun getEntityResults(
    personaIds: Set<Id>,
    entityStore: EntityStore,
    addressBook: AddressBook,
    fileStore: ContentAddressedFileStore,
    eventBus: EventBus,
    entityIds: Set<Id>,
): List<EntityResult> {
    if (entityIds.isEmpty()) {
        return emptyList()
    }

    // TODO: Make a feed context that holds the address book, entities, children, etc.
    val authoritiesById = getAuthoritiesById(addressBook, personaIds)
    val authorityIds = authoritiesById.values.filter { it.personaId in personaIds }.map { it.entityId }.toSet()
    val entities = entityStore.getEntities(authorityIds, entityIds).filter { isEntityIsVisible(it) }
    val children = getChildren(authorityIds, entityStore, entities)
    val entitiesByEntityId = entities.groupBy { it.entityId }
    val activitiesByEntityId = activitiesByEntityId(authoritiesById, entities, children)

    // TODO: Filter out entities with missing attachments?
    requestMissingAttachmentIds(fileStore, eventBus, entities)

    return entityIds
        .filter { id -> entitiesByEntityId[id]?.any { it !is RawEntity } ?: false }
        .map {
            val entitiesForId = entitiesByEntityId[it]!!
            val activities = activitiesByEntityId[it]!!
            EntityResult(
                it,
                getPersonaId(addressBook, activities),
                getSummary(entitiesForId, authoritiesById),
                activities
            )
        }
    // .sortedByDescending { it.activities.maxOf{ it.epochSecond} }
}

fun getEntityResult(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    personaId: Id,
    entityId: Id
): EntityResult? {
    val personaIds = context.personaIds.ifEmpty { setOf(personaId) }
    return getEntityResults(personaIds, entityStore, addressBook, fileStore, eventBus, setOf(entityId)).firstOrNull()
}

fun handleGetFeed(
    entityStore: EntityStore,
    queryParser: QueryParser,
    searchIndex: SearchIndex,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    personaIds: Set<Id>,
    limit: Int,
    pagingToken: String? = null,
    queryString: String?
): FeedResult {
    logger.info { "Getting feed for ${personaIds.joinToString { it.toString() }}" }
    // TODO: This could be tightened up. We're accessing the address book multiple times. Could pass entries around instead
    val authorityIds = addressBook.getEntries().filter { it.personaId in personaIds }.map { it.entityId }.toSet()
    val query = queryParser.parse(queryString ?: "", authorityIds)
    val pageableEntityIds = getEntityIds(entityStore, searchIndex, query, limit, pagingToken)
    val context = Context(personaIds)
    return FeedResult(
        context,
        pageableEntityIds.pagingToken,
        getEntityResults(authorityIds, entityStore, addressBook, fileStore, eventBus, pageableEntityIds.entityIds)
    )
}

// TODO: Move to this pattern for all calls - cleans up calling code.
fun Application.handleGetFeed(
    personaIds: Set<Id> = emptySet(),
    limit: Int = 25,
    pagingToken: String? = null,
    queryString: String? = null
): FeedResult {
    return handleGetFeed(
        inject(),
        inject(),
        inject(),
        inject(),
        inject(),
        inject(),
        personaIds,
        limit,
        pagingToken,
        queryString
    )
}