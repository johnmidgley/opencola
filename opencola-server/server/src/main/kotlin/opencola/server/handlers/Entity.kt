package opencola.server.handlers

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.content.HtmlParser
import io.opencola.io.HttpClient
import io.opencola.io.urlRegex
import io.opencola.model.*
import io.opencola.util.blankToNull
import io.opencola.util.ifNullOrElse
import io.opencola.util.nullOrElse
import io.opencola.storage.AddressBook
import io.opencola.storage.EntityStore
import io.opencola.storage.FileStore
import io.opencola.storage.PersonaAddressBookEntry
import java.net.URI

private val logger = KotlinLogging.logger("EntityHandler")
private val httpClient = HttpClient()

suspend fun getEntity(call: ApplicationCall, persona: PersonaAddressBookEntry, entityStore: EntityStore, addressBook: AddressBook) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.decode(stringId)
    val entityResult = getEntityResult(entityStore, addressBook, Context(""), persona.personaId, entityId)

    if (entityResult != null)
        call.respond(entityResult)
}

// TODO - investigate delete and then re-add. It seems to "restore" all previous saves. Is this good or bad?
fun deleteEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id,
): EntityResult? {
    logger.info { "Deleting $entityId" }
    entityStore.deleteEntities(persona.personaId, entityId)
    return getEntityResult(entityStore, addressBook, context, persona.personaId, entityId)
}

@Serializable
data class EntityPayload(
    val entityId: String? = null,
    val name: String? = null,
    val imageUri: String? =null,
    val description: String? = null,
    val like: Boolean? = null,
    val tags: String? = null,
    val comment: String? = null,
    val attachments: List<String>? = null,
)

fun updateEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
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
    entity.tags = entityPayload.tags
        ?.let { tags ->
            tags
                .split(" ")
                .filter { it.isNotBlank() }
        }?.toList() ?: emptyList()

    entity.attachmentIds = entityPayload.attachments?.map { Id.decode(it) } ?: emptyList()

    if (entityPayload.comment.isNullOrBlank())
        entityStore.updateEntities(entity)
    else
        entityStore.updateEntities(entity, CommentEntity(entity.authorityId, entity.entityId, entityPayload.comment))

    return getEntityResult(entityStore, addressBook, context, persona.entityId, entity.entityId)
}

fun updateEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
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
        updateEntity(entityStore, addressBook, context, persona, entity, entityPayload)
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

        else -> throw IllegalArgumentException("Don't know how to add ${existingEntity.javaClass.simpleName}")
    }

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

    val entity = getOrCopyEntity(personaId, entityStore, entityId)
        ?: throw IllegalArgumentException("Attempt to add comment to unknown entity")

    val commentEntity =
        if (commentId == null)
            CommentEntity(personaId, entity.entityId, text)
        else
            entityStore.getEntity(personaId, commentId) as? CommentEntity
                ?: throw IllegalArgumentException("Unknown comment: $commentId")

    commentEntity.text = text
    entityStore.updateEntities(entity, commentEntity)

    return commentEntity
}

@Serializable
data class PostCommentPayload(val commentId: String? = null, val text: String)

fun updateComment(
    entityStore: EntityStore,
    addressBook: AddressBook,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id,
    comment: PostCommentPayload
): EntityResult? {
    updateComment(persona, entityStore, entityId, comment.commentId.nullOrElse { Id.decode(it) }, comment.text)
    return getEntityResult(entityStore, addressBook, context, persona.personaId, entityId)
}

suspend fun deleteComment(call: ApplicationCall, persona: PersonaAddressBookEntry, entityStore: EntityStore) {
    val commentId = Id.decode(call.parameters["commentId"] ?: throw IllegalArgumentException("No commentId specified"))
    entityStore.deleteEntities(persona.personaId, commentId)
    call.respondText("{}")
}

fun saveEntity(
    entityStore: EntityStore,
    addressBook: AddressBook,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityId: Id
): EntityResult? {
    val entity = getOrCopyEntity(persona.personaId, entityStore, entityId)
        ?: throw IllegalArgumentException("Unable to save unknown entity: $entityId")

    entityStore.updateEntities(entity)
    return getEntityResult(entityStore, addressBook, context, persona.entityId, entityId)
}

fun newResourceFromUrl(
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook,
    url: String
): EntityResult? {
    try {
        val resource = entityStore.getEntity(persona.personaId, Id.ofUri(URI(url)))
            ?: ResourceEntity(persona.personaId, URI(url))

        // TODO: What if URL isn't html?
        // TODO: If parsing fails, could call getOrCopyEntity(persona.personaId, entityStore, Id.ofUri(URI(url)))
        val parser = HtmlParser(httpClient.get(url))

        resource.name = parser.parseTitle()
        resource.description = parser.parseDescription()
        resource.imageUri = parser.parseImageUri()
        entityStore.updateEntities(resource)

        return getEntityResult(entityStore, addressBook, Context(""), persona.personaId, resource.entityId)
    } catch (e: Exception) {
        logger.error { e }
    }

    return null
}

fun newPost(
    entityStore: EntityStore,
    addressBook: AddressBook,
    context: Context,
    persona: PersonaAddressBookEntry,
    entityPayload: EntityPayload
): EntityResult? {
    val url = entityPayload.description?.trim()

    if (url != null && urlRegex.matchEntire(url) != null) {
        val result = newResourceFromUrl(persona, entityStore, addressBook, url)
        if (result != null)
            return result
    }

    return updateEntity(entityStore, addressBook, context, persona, PostEntity(persona.personaId), entityPayload)
}

suspend fun addAttachment(
    entityStore: EntityStore,
    fileStore: FileStore,
    addressBook: AddressBook,
    context: Context,
    personaId: Id,
    entityId: Id,
    multipart: MultiPartData
): EntityResult? {
    val dataEntities = getDataEntities(entityStore, fileStore, personaId, multipart)
    val entity =
        entityStore.getEntity(personaId, entityId) ?: throw IllegalArgumentException("Unknown entity: $entityId")
    entity.attachmentIds += dataEntities.map { it.entityId }
    entityStore.updateEntities(entity, *dataEntities.toTypedArray())
    return getEntityResult(entityStore, addressBook, context, personaId, entityId)
}

fun deleteAttachment(
    entityStore: EntityStore,
    addressBook: AddressBook,
    context: Context,
    personaId: Id,
    entityId: Id,
    attachmentId: Id
): EntityResult? {
    val entity =
        entityStore.getEntity(personaId, entityId) ?: throw IllegalArgumentException("Unknown entity: $entityId")
    entity.attachmentIds -= attachmentId
    entityStore.updateEntities(entity)
    return getEntityResult(entityStore, addressBook, context, personaId, entityId)
}