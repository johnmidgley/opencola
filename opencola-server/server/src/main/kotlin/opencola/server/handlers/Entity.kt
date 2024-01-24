/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package opencola.server.handlers

import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.opencola.application.Application
import io.opencola.content.*
import io.opencola.event.bus.EventBus
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import io.opencola.io.HttpClient
import io.opencola.io.urlRegex
import io.opencola.model.*
import io.opencola.util.blankToNull
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
    entity.imageUri = entityPayload.imageUri.blankToNull()?.let {
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
    return getOrCopyEntity(addressBook, entityStore, persona.personaId, entityId)?.let { entity ->
        // TODO: getOrCopy has already copied the entity (if it didn't exist) in a different transaction. Consider
        //  moving the getOrCopyEntity call into the updateEntity call and returning saving + update in a single
        //  transaction
        updateEntity(entityStore, addressBook, eventBus, fileStore, context, persona, entity, entityPayload)
    }
}

// This is not a full copy, rather a copy of all the fields that need to be copied when an item is bubbled. In particular,
// It doesn't copy any activities
fun copyEntity(authorityId: Id, entity: Entity): Entity {
    val copy = when (entity) {
        is ResourceEntity -> {
            ResourceEntity(
                authorityId,
                entity.uri!!, // TODO: Get rid of !! - make non nullable
                entity.name,
                entity.description,
                entity.text,
                entity.imageUri
            )

        }

        is PostEntity -> {
            PostEntity(
                authorityId,
                entity.entityId,
                entity.name,
                entity.description,
                entity.text,
                entity.imageUri
            )
        }

        is DataEntity -> {
            DataEntity(
                authorityId,
                entity.entityId,
                entity.mimeType!!,
                entity.name,
                entity.description,
                entity.text,
                entity.imageUri
            )
        }

        else -> throw IllegalArgumentException("Don't know how to copy ${entity.javaClass.simpleName}")
    }

    copy.attachmentIds = entity.attachmentIds

    return copy
}

// This is not a full copy, rather a copy of all the fields that need to be copied when an item is saved. In particular,
// It doesn't copy any activities, EXCEPT attachments, which are part of the content of a post
fun copyEntity(fromEntity: Entity, toEntity: Entity): Entity {
    require(fromEntity.javaClass == toEntity.javaClass) { "Source and destination entities must be of the same type" }
    toEntity.name = fromEntity.name
    toEntity.description = fromEntity.description
    toEntity.text = fromEntity.text
    toEntity.imageUri = fromEntity.imageUri
    toEntity.attachmentIds += fromEntity.attachmentIds

    when (fromEntity) {
        is ResourceEntity -> {
            (toEntity as ResourceEntity).apply {
                uri = fromEntity.uri
            }
        }

        is PostEntity -> toEntity as PostEntity

        is DataEntity -> {
            (toEntity as DataEntity).apply {
                mimeType = fromEntity.mimeType
            }
        }

        else -> throw IllegalArgumentException("Don't know how to copy ${fromEntity.javaClass.simpleName}")
    }

    return toEntity
}

fun computeOriginDistance(addressBook: AddressBook, entities: Iterable<Entity>): Int? {
    require(entities.map { it.entityId }.distinct().size <= 1) { "All entities must have the same entityId" }
    val personaIds = addressBook.getEntries().map { it.personaId }

    if (entities.any { it.authorityId in personaIds })
        return null // Equivalent to 0

    return entities.minOf { it.originDistance ?: 0 } + 1
}

fun getOrCopyEntity(addressBook: AddressBook, entityStore: EntityStore, authorityId: Id, entityId: Id): Entity? {
    val entityFromAuthority = entityStore.getEntity(authorityId, entityId)

    if (entityFromAuthority != null && entityFromAuthority !is RawEntity) {
        // Entity already exists
        return entityFromAuthority
    }

    val existingEntities = entityStore.getEntities(emptySet(), setOf(entityId))
    val existingEntity = existingEntities
        .filterNot { it is RawEntity }
        .firstOrNull()

    if (existingEntity == null) {
        // No entity to copy
        return null
    }

    val newEntity =
        entityFromAuthority
            ?.let { copyEntity(existingEntity, (it as RawEntity).setType(existingEntity.type!!)) }
            ?: copyEntity(authorityId, existingEntity)

    newEntity.originDistance = computeOriginDistance(addressBook, existingEntities)

    // TODO: Remove any calls to update entity after calling this method
    entityStore.updateEntities(newEntity)
    return newEntity
}

fun updateComment(
    entityStore: EntityStore,
    persona: PersonaAddressBookEntry,
    entityId: Id,
    commentId: Id?,
    text: String
): CommentEntity {
    logger.info { "Adding comment to $entityId" }
    val personaId = persona.personaId

    val parent = entityStore.getEntities(emptySet(), setOf(entityId)).firstOrNull()
    require(parent != null) { "Attempt to add comment to unknown entity" }
    // If the parent is a comment, then this is a reply, the topLevelParentId should be set
    val topLevelParentId = (parent as? CommentEntity)?.let { it.topLevelParentId ?: it.parentId }

    val commentEntity =
        if (commentId == null)
            CommentEntity(personaId, entityId, text, topLevelParentId)
        else
            entityStore.getEntity(personaId, commentId) as? CommentEntity
                ?: throw IllegalArgumentException("Unknown comment: $commentId")

    commentEntity.text = text
    entityStore.updateEntities(commentEntity)

    return commentEntity
}

fun Application.updateComment(persona: PersonaAddressBookEntry, entityId: Id, commentId: Id?, text: String) =
    updateComment(inject(), persona, entityId, commentId, text)

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
    val updatedComment = updateComment(entityStore, persona, entityId, comment.commentId?.let { Id.decode(it) }, comment.text)
    val topLevelParentId = updatedComment.topLevelParentId ?: updatedComment.parentId ?: entityId
    return getEntityResult(entityStore, addressBook, eventBus, fileStore, context, persona.personaId, topLevelParentId)
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
    val entity = getOrCopyEntity(addressBook, entityStore, persona.personaId, entityId)
        ?: throw IllegalArgumentException("Unable to save unknown entity: $entityId")

    // TODO: Should DB enforce that data id exists? Seems valid to point to data that isn't available locally, but think on it
    val attachmentEntities = entity.attachmentIds.map {
        getOrCopyEntity(addressBook, entityStore, persona.personaId, it)
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