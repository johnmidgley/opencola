package opencola.server.handlers

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.request.*
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
import io.opencola.storage.PersonaAddressBookEntry
import java.net.URI

private val logger = KotlinLogging.logger("EntityHandler")
private val httpClient = HttpClient()

suspend fun getEntity(call: ApplicationCall, persona: PersonaAddressBookEntry, entityStore: EntityStore, addressBook: AddressBook) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.decode(stringId)
    val entityResult = getEntityResults(persona, entityStore, addressBook, setOf(entityId)).firstOrNull()

    if (entityResult != null)
        call.respond(entityResult)
}

// TODO - investigate delete and then re-add. It seems to "restore" all previous saves. Is this good or bad?
suspend fun deleteEntity(
    call: ApplicationCall,
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook
) {
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.decode(stringId)

    logger.info { "Deleting $entityId" }
    entityStore.deleteEntity(persona.personaId, entityId)
    val entity = getEntityResults(persona, entityStore, addressBook, setOf(entityId)).firstOrNull()

    if (entity == null)
    // Need to return something in JSON. Sending an {} means that the entity has been fully deleted (i.e. no other
    // peers have the item, so this is a final delete)
        call.respond("{}")
    else
        call.respond(entity)
}

@Serializable
data class EntityPayload(
    val entityId: String?,
    val name: String?,
    val imageUri: String?,
    val description: String?,
    val like: Boolean?,
    val tags: String?,
    val comment: String?,
)

fun updateEntity(
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook,
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
        .blankToNull()
        .ifNullOrElse(emptySet()) { it.split(" ").toSet() }

    if (entityPayload.comment.isNullOrBlank())
        entityStore.updateEntities(entity)
    else
        entityStore.updateEntities(entity, CommentEntity(entity.authorityId, entity.entityId, entityPayload.comment))

    return getEntityResult(persona, entityStore, addressBook, entity.entityId)
}

suspend fun updateEntity(
    call: ApplicationCall,
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook
) {
    val authorityId = persona.personaId
    val entityPayload = call.receive<EntityPayload>()
    val entityId =
        Id.decode(entityPayload.entityId ?: throw IllegalArgumentException("No entityId specified for update"))
    logger.info { "Updating: $entityPayload" }

    val entity = getOrCopyEntity(authorityId, entityStore, entityId)
    if (entity == null) {
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    updateEntity(persona, entityStore, addressBook, entity, entityPayload).nullOrElse { call.respond(it) }
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

    return newEntity
}

fun addComment(
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

suspend fun addComment(
    call: ApplicationCall,
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook
) {
    val entityId = Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
    val comment = call.receive<PostCommentPayload>()

    addComment(persona, entityStore, entityId, comment.commentId.nullOrElse { Id.decode(it) }, comment.text)
    getEntityResults(persona, entityStore, addressBook, setOf(entityId))
        .firstOrNull()
        .nullOrElse { call.respond(it) }
}

suspend fun deleteComment(call: ApplicationCall, persona: PersonaAddressBookEntry, entityStore: EntityStore) {
    val commentId = Id.decode(call.parameters["commentId"] ?: throw IllegalArgumentException("No commentId specified"))

    entityStore.deleteEntity(persona.personaId, commentId)
    call.respondText("{}")
}

suspend fun saveEntity(
    call: ApplicationCall,
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook
) {
    val entityId = Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))

    val entity = getOrCopyEntity(persona.personaId, entityStore, entityId)
        ?: throw IllegalArgumentException("Unable to save unknown entity: $entityId")

    entityStore.updateEntities(entity)
    getEntityResults(persona, entityStore, addressBook, setOf(entityId))
        .firstOrNull()
        .nullOrElse { call.respond(it) }
}

fun newResourceFromUrl(
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook,
    url: String
): EntityResult? {
    try {
        val entity = getOrCopyEntity(persona.personaId, entityStore, Id.ofUri(URI(url)))
            ?: run {
                // TODO: What if URL isn't html?
                val parser = HtmlParser(httpClient.get(url))
                val resource = ResourceEntity(
                    persona.personaId,
                    URI(url),
                    parser.parseTitle(),
                    parser.parseDescription(),
                    null, // TODO: Grab proper text
                    parser.parseImageUri()
                )
                entityStore.updateEntities(resource)
                resource
            }

        return getEntityResult(persona, entityStore, addressBook, entity.entityId)
    } catch (e: Exception) {
        logger.error { e }
    }

    return null
}

fun newPost(
    persona: PersonaAddressBookEntry,
    entityStore: EntityStore,
    addressBook: AddressBook,
    entityPayload: EntityPayload
): EntityResult? {
    val url = entityPayload.description?.trim()

    if (url != null && urlRegex.matchEntire(url) != null) {
        val result = newResourceFromUrl(persona, entityStore, addressBook, url)
        if (result != null)
            return result
    }

    return updateEntity(persona, entityStore, addressBook, PostEntity(persona.personaId), entityPayload)
}

suspend fun newPost(call: ApplicationCall, persona: PersonaAddressBookEntry, entityStore: EntityStore, addressBook: AddressBook) {
    val entityPayload = call.receive<EntityPayload>()
    newPost(persona, entityStore, addressBook, entityPayload).nullOrElse { call.respond(it) }
}