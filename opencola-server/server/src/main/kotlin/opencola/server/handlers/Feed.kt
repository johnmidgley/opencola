package opencola.server.handlers

import io.opencola.event.EventBus
import io.opencola.event.Events
import io.opencola.model.*
import kotlinx.serialization.Serializable
import io.opencola.util.nullOrElse
import io.opencola.model.CoreAttribute.*
import io.opencola.search.SearchIndex
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.addressbook.AddressBookEntry
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import io.opencola.storage.filestore.ContentBasedFileStore
import mu.KotlinLogging
import opencola.server.handlers.EntityResult.*

private val logger = KotlinLogging.logger("Feed")

@Serializable
// TODO: Make context: String? constructor private?
data class FeedResult(val context: String?, val pagingToken: String?, val results: List<EntityResult>) {
    constructor(context: Context, pagingToken: String?, results: List<EntityResult>) : this(
        context.toString(),
        pagingToken,
        results
    )
}

fun entityAttributeAsString(entity: Entity, attribute: Attribute): String? {
    return entity.getValue(attribute.name).nullOrElse { it.get().toString() }
}

fun getPostedById(entities: List<Entity>): Id {
    return entities.minByOrNull {
        it.getAllFacts().minByOrNull { fact -> fact.epochSecond!! }!!.epochSecond!!
    }!!.authorityId
}

fun getSummary(entities: List<Entity>, authoritiesById: Map<Id, AddressBookEntry>): Summary {
    val entity = entities.maxByOrNull { e -> e.getCurrentFacts().maxOfOrNull { it.transactionOrdinal!! }!! }!!
    val postedByAuthority = authoritiesById[getPostedById(entities)]

    return Summary(
        entityAttributeAsString(entity, Name.spec),
        entityAttributeAsString(entity, Uri.spec),
        entityAttributeAsString(entity, Description.spec),
        entityAttributeAsString(entity, ImageUri.spec),
        postedByAuthority?.name,
        postedByAuthority?.imageUri?.toString()
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
            Action(ActionType.Comment, commentId, children.comments.getValue(commentId).text)
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
                    authoritiesById[entity.authorityId].nullOrElse {
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
        searchIndex.getResults(query, 100, authorityIds, null).items.map { it.entityId }
    }

    return entityIds.toSet()
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
    val comments = children.filterIsInstance<CommentEntity>().associateBy { it.entityId }
    // TODO: Attachments are not necessarily unique. Should select best one
    val attachments = children.filterIsInstance<DataEntity>().associateBy { it.entityId }

    return Children(comments, attachments)
}

fun getAuthoritiesById(addressBook: AddressBook, personaIds: Set<Id>): Map<Id, AddressBookEntry> {
    return addressBook.getEntries()
        .filter { it.personaId in personaIds }
        .map {
            if (it is PersonaAddressBookEntry)
                AddressBookEntry(
                    it.personaId,
                    it.entityId,
                    "You (${it.name})",
                    it.publicKey,
                    it.address,
                    it.imageUri,
                    it.isActive
                )
            else
                it
        }.associateBy { it.entityId }
}

fun getPersonaId(addressBook: AddressBook, activities: List<Activity>): Id {
    val authorities = addressBook.getEntries()
    return activities.maxByOrNull { it.epochSecond }?.let { activity ->
        authorities.firstOrNull { it.entityId.toString() == activity.authorityId }?.personaId
    } ?: authorities.first { it is PersonaAddressBookEntry }.personaId
}

fun requestMissingAttachmentIds(
    fileStore: ContentBasedFileStore,
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
    fileStore: ContentBasedFileStore,
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
        .filter { entitiesByEntityId.containsKey(it) }
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
}

fun getEntityResult(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentBasedFileStore,
    context: Context,
    personaId: Id,
    entityId: Id
): EntityResult? {
    val personaIds = context.personaIds.ifEmpty { setOf(personaId) }
    return getEntityResults(personaIds, entityStore, addressBook, fileStore, eventBus, setOf(entityId)).firstOrNull()
}

fun handleGetFeed(
    personaIds: Set<Id>,
    entityStore: EntityStore,
    searchIndex: SearchIndex,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentBasedFileStore,
    query: String?
): FeedResult {
    logger.info { "Getting feed for ${personaIds.joinToString { it.toString() }}" }
    // TODO: This could be tightened up. We're accessing the address book multiple times. Could pass entries around instead
    val authorityIds = addressBook.getEntries().filter { it.personaId in personaIds }.map { it.entityId }.toSet()
    val entityIds = getEntityIds(entityStore, authorityIds, searchIndex, query)
    val context = Context(personaIds)
    return FeedResult(
        context,
        null,
        getEntityResults(authorityIds, entityStore, addressBook, fileStore, eventBus, entityIds)
    )
}