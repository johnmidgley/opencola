package opencola.server.handlers

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.opencola.content.*
import io.opencola.event.bus.EventBus
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.io.HttpClient
import io.opencola.io.urlRegex
import io.opencola.model.*
import io.opencola.util.blankToNull
import io.opencola.util.nullOrElse
import io.opencola.storage.addressbook.AddressBook
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import io.opencola.storage.addressbook.PersonaAddressBookEntry
import java.net.URI

private val logger = KotlinLogging.logger("EntityHandler")
private val httpClient = HttpClient()

suspend fun getEntity(
    call: ApplicationCall,
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.decode(stringId)
    val entityResult =
        getEntityResult(entityStore, addressBook, eventBus, fileStore, Context(""), persona.personaId, entityId)

    if (entityResult != null)
        call.respond(entityResult)
}

// TODO - investigate delete and then re-add. It seems to "restore" all previous saves. Is this good or bad?
fun deleteEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id,
): EntityResult? {
    logger.info { "Deleting $entityId" }
    entityStore.deleteEntities(persona.personaId, entityId)
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, persona.personaId, entityId)
}

@Serializable
data class EntityPayload(
    val entityId: String? = null,
    val name: String? = null,
    val imageUri: String? = null,
    val description: String? = null,
    val like: Boolean? = null,
    val tags: String? = null,
    val comment: String? = null,
    val attachments: List<String>? = null,
)

fun updateEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entity: Entity,
    entityPayload: EntityPayload
): EntityResult? {
    entity.name = entityPayload.name.blankToNull()
    entity.imageUri = entityPayload.imageUri.blankToNull().nullOrElse {
        val uri = URI(it)
        if (!uri.isAbsolute)
            throw IllegalArgumentException("Image URI must be absolute")
        uri
    }
    entity.description = entityPayload.description.blankToNull()
    entity.like = entityPayload.like
    entity.tags = getTags(entityPayload.tags)
    entity.attachmentIds = entityPayload.attachments?.map { Id.decode(it) } ?: emptyList()

    if (entityPayload.comment.isNullOrBlank())
        entityStore.updateEntities(entity)
    else
        entityStore.updateEntities(entity, CommentEntity(entity.authorityId, entity.entityId, entityPayload.comment))

    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, persona.entityId, entity.entityId)
}

fun updateEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityPayload: EntityPayload
): EntityResult? {
    val entityId =
        Id.decode(entityPayload.entityId ?: throw IllegalArgumentException("No entityId specified for update"))
    logger.info { "Updating: $entityPayload" }
    return getOrCopyEntity(persona.personaId, entityStore, entityId)?.let { entity ->
        // TODO: getOrCopy has already copied the entity (if it didn't exist) in a different transaction. Consider
        //  moving the getOrCopyEntity call into the updateEntity call and returning saving + update in a single
        //  transaction
        updateEntity(entityStore, addressBook, eventBus, fileStore, context, persona, entity, entityPayload)
    }
}

fun getOrCopyEntity(authorityId: Id, entityStore: EntityStore, entityId: Id): Entity? {
    val existingEntity =
        entityStore.getEntity(authorityId, entityId) ?: entityStore.getEntities(emptySet(), setOf(entityId))
            .firstOrNull()

    if (existingEntity == null || existingEntity.authorityId == authorityId)
        return existingEntity

    val newEntity = when (existingEntity) {
        is ResourceEntity -> {
            ResourceEntity(
                authorityId,
                existingEntity.uri!!, // TODO: Get rid of !! - make non nullable
                existingEntity.name,
                existingEntity.description,
                existingEntity.text,
                existingEntity.imageUri
            )

        }

        is PostEntity -> {
            PostEntity(
                authorityId,
                existingEntity.entityId,
                existingEntity.name,
                existingEntity.description,
                existingEntity.text,
                existingEntity.imageUri
            )
        }

        is DataEntity -> {
            DataEntity(
                authorityId,
                existingEntity.entityId,
                existingEntity.mimeType!!,
                existingEntity.name,
                existingEntity.description,
                existingEntity.text,
                existingEntity.imageUri
            )
        }

        else -> throw IllegalArgumentException("Don't know how to add ${existingEntity.javaClass.simpleName}")
    }

    newEntity.attachmentIds = existingEntity.attachmentIds

    // TODO: Remove any calls to update entity after calling this (getOrCopyEntity)
    entityStore.updateEntities(newEntity)
    return newEntity
}

fun updateComment(
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    entityId: Id,
    commentId: Id?,
    text: String
): CommentEntity {
    logger.info { "Adding comment to $entityId" }
    val personaId = persona.personaId

    require(
        entityStore.getEntities(emptySet(), setOf(entityId)).isNotEmpty()
    ) { "Attempt to add comment to unknown entity" }

    val commentEntity =
        if (commentId == null)
            CommentEntity(personaId, entityId, text)
        else
            entityStore.getEntity(personaId, commentId) as? CommentEntity
                ?: throw IllegalArgumentException("Unknown comment: $commentId")

    commentEntity.text = text
    entityStore.updateEntities(commentEntity)

    return commentEntity
}

@Serializable
data class PostCommentPayload(val commentId: String? = null, val text: String)

fun updateComment(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id,
    comment: PostCommentPayload
): EntityResult? {
    updateComment(persona, entityStore, entityId, comment.commentId.nullOrElse { Id.decode(it) }, comment.text)
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, persona.personaId, entityId)
}

suspend fun deleteComment(call: ApplicationCall, persona: PersonaAddressBookEntry, entityStore: EntityStore) {
    val commentId = Id.decode(call.parameters["commentId"] ?: throw IllegalArgumentException("No commentId specified"))
    entityStore.deleteEntities(persona.personaId, commentId)
    call.respondText("{}")
}

fun saveEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id
): EntityResult? {
    val entity = getOrCopyEntity(persona.personaId, entityStore, entityId)
        ?: throw IllegalArgumentException("Unable to save unknown entity: $entityId")

    // TODO: Should DB enforce that data id exists? Seems valid to point to data that isn't available locally, but think on it
    val attachmentEntities = entity.attachmentIds.map {
        getOrCopyEntity(persona.personaId, entityStore, it)
            ?: throw IllegalArgumentException("Unable to save unknown attachment: $it")
    }

    entityStore.updateEntities(entity, *attachmentEntities.toTypedArray())
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, persona.entityId, entityId)
}

// TODO: Abstract out ContentParser interface and collapse these two methods
fun updateEntityFromHtmlContent(resource: Entity, uri: URI, content: ByteArray): Entity {
    val parser = HtmlContentParser(content, uri)
    resource.name = parser.parseTitle()
    resource.description = parser.parseDescription()
    resource.imageUri = parser.parseImageUri()
    resource.text = parser.parseText()
    return resource
}

fun updateEntityFromPdfContent(resource: Entity, uri: URI, content: ByteArray): Entity {
    val parser = OcPdfContentParser(TextExtractor(), content, uri)
    resource.name = parser.parseTitle()
    resource.description = parser.parseDescription()
    resource.imageUri = parser.parseImageUri()
    resource.text = parser.parseText()
    return resource
}

fun updateResourceFromSource(contentTypeDetector: ContentTypeDetector, resourceEntity: ResourceEntity): Boolean {
    val uri = resourceEntity.uri

    try {
        require(uri != null) { "ResourceEntity must have uri" }
        val content = httpClient.getContent(uri.toString())

        // TODO: Make a ContentParser registry
        when (val contentType = contentTypeDetector.getType(content)) {
            "text/html" -> updateEntityFromHtmlContent(resourceEntity, uri, content)
            "application/pdf" -> updateEntityFromPdfContent(resourceEntity, uri, content)
            else -> {
                throw IllegalArgumentException("Unhandled Content type: $contentType for $uri")
            }
        }

        return true
    } catch (e: Exception) {
        logger.error { e }
    }

    return false
}

fun newResourceFromUri(
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    eventBus: EventBus,
    addressBook: AddressBook,
    fileStore: ContentAddressedFileStore,
    contentTypeDetector: ContentTypeDetector,
    uri: URI
): EntityResult? {
    val resource = entityStore.getEntity(persona.personaId, Id.ofUri(uri)) as? ResourceEntity
        ?: ResourceEntity(persona.personaId, uri)

    if (!updateResourceFromSource(contentTypeDetector, resource)) {
        if (resource.name == null)
        // Couldn't parse anything, so just use the url as the name
            resource.name = uri.toString()
    }

    entityStore.updateEntities(resource)

    return getEntityResult(
        entityStore,
        addressBook,
        eventBus,
        fileStore,
        Context(""),
        persona.personaId,
        resource.entityId
    )
}

fun newPost(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    contentTypeDetector: ContentTypeDetector,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityPayload: EntityPayload,
    ocServerPorts: Set<Int>,
): EntityResult? {
    val url = entityPayload.description?.trim()

    if (url != null && urlRegex.matchEntire(url) != null) {
        val uri = URI(url).also { requireNotLocalOCAddress(it, ocServerPorts) }
        val result =
            newResourceFromUri(persona, entityStore, eventBus, addressBook, fileStore, contentTypeDetector, uri)
        // TODO: Handle comment and other fields
        if (result != null)
            return result
    }

    return updateEntity(
        entityStore,
        addressBook,
        eventBus,
        fileStore,
        context,
        persona,
        PostEntity(persona.personaId),
        entityPayload
    )
}

suspend fun addAttachment(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    personaId: Id,
    entityId: Id,
    multipart: MultiPartData
): EntityResult? {
    val dataEntities = getDataEntities(entityStore, fileStore, personaId, multipart)
    val entity =
        entityStore.getEntity(personaId, entityId) ?: RawEntity(personaId, entityId)
    entity.attachmentIds += dataEntities.map { it.entityId }
    entityStore.updateEntities(entity, *dataEntities.toTypedArray())
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, personaId, entityId)
}

fun deleteAttachment(
    entityStore: EntityStore,
    addressBook: AddressBook,
    eventBus: EventBus,
    fileStore: ContentAddressedFileStore,
    context: Context,
    personaId: Id,
    entityId: Id,
    attachmentId: Id
): EntityResult? {
    val entity =
        entityStore.getEntity(personaId, entityId) ?: throw IllegalArgumentException("Unknown entity: $entityId")
    entity.attachmentIds -= attachmentId
    entityStore.updateEntities(entity)
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, personaId, entityId)
}