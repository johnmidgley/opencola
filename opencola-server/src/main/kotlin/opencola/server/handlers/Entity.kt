package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.content.HtmlParser
import opencola.core.content.HttpClient
import opencola.core.content.urlRegex
import opencola.core.extensions.blankToNull
import opencola.core.extensions.ifNullOrElse
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import opencola.service.EntityResult
import java.net.URI

private val logger = KotlinLogging.logger("EntityHandler")
private val httpClient = HttpClient()

suspend fun getEntity(call: ApplicationCall, authority: Authority, entityStore: EntityStore, addressBook: AddressBook) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.decode(stringId)
    val entityResult = getEntityResults(authority, entityStore, addressBook, setOf(entityId)).firstOrNull()

    if (entityResult != null)
        call.respond(entityResult)
}

// TODO - investigate delete and then re-add. It seems to "restore" all previous saves. Is this good or bad?
suspend fun deleteEntity(call: ApplicationCall, authority: Authority, entityStore: EntityStore, addressBook: AddressBook) {
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.decode(stringId)

    logger.info { "Deleting $entityId" }
    entityStore.deleteEntity(authority.authorityId, entityId)
    val entity = getEntityResults(authority, entityStore, addressBook, setOf(entityId)).firstOrNull()

    if(entity == null)
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

fun updateEntity(authority: Authority, entityStore: EntityStore, addressBook: AddressBook, entity: Entity, entityPayload: EntityPayload): EntityResult? {
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

    if(entityPayload.comment.isNullOrBlank())
        entityStore.updateEntities(entity)
    else
        entityStore.updateEntities(entity, CommentEntity(entity.authorityId, entity.entityId, entityPayload.comment))

    return getEntityResult(authority, entityStore, addressBook, entity.entityId)
}

suspend fun updateEntity(call: ApplicationCall, authority: Authority, entityStore: EntityStore, addressBook: AddressBook) {
    val authorityId = authority.authorityId
    val entityPayload = call.receive<EntityPayload>()
    val entityId = Id.decode(entityPayload.entityId ?: throw IllegalArgumentException("No entityId specified for update"))
    logger.info { "Updating: $entityPayload" }

    val entity = getOrCopyEntity(authorityId, entityStore, entityId)
    if(entity == null){
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    updateEntity(authority, entityStore, addressBook, entity, entityPayload).nullOrElse { call.respond(it) }
}

fun getOrCopyEntity(authorityId : Id, entityStore: EntityStore, entityId: Id): Entity? {
    val existingEntity = entityStore.getEntity(authorityId, entityId) ?:
        entityStore.getEntities(emptySet(), setOf(entityId)).firstOrNull()

    if(existingEntity == null || existingEntity.authorityId == authorityId )
        return existingEntity

    val newEntity = when(existingEntity){
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
            PostEntity(authorityId,
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
    authority: Authority,
    entityStore: EntityStore,
    entityId: Id,
    commentId: Id?,
    text: String
): CommentEntity {
    logger.info { "Adding comment to $entityId" }
    val authorityId = authority.authorityId

    val entity = getOrCopyEntity(authorityId, entityStore, entityId)
        ?: throw IllegalArgumentException("Attempt to add comment to unknown entity")

    val commentEntity =
        if (commentId == null)
            CommentEntity(authorityId, entity.entityId, text)
        else
            entityStore.getEntity(authorityId, commentId) as? CommentEntity
                ?: throw IllegalArgumentException("Unknown comment: $commentId")

    commentEntity.text = text
    entityStore.updateEntities(entity, commentEntity)

    return commentEntity
}

@Serializable
data class PostCommentPayload(val commentId: String? = null, val text: String)

suspend fun addComment(call: ApplicationCall, authority: Authority, entityStore: EntityStore, addressBook: AddressBook) {
    val entityId = Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
    val comment = call.receive<PostCommentPayload>()

    addComment(authority, entityStore, entityId, comment.commentId.nullOrElse { Id.decode(it) }, comment.text)
    getEntityResults(authority, entityStore, addressBook, setOf(entityId))
        .firstOrNull()
        .nullOrElse { call.respond(it) }
}

suspend fun deleteComment(call: ApplicationCall, authority: Authority, entityStore: EntityStore) {
    val commentId = Id.decode(call.parameters["commentId"] ?: throw IllegalArgumentException("No commentId specified"))

    entityStore.deleteEntity(authority.authorityId, commentId)
    call.respondText("{}")
}

suspend fun saveEntity(call: ApplicationCall, authority: Authority, entityStore: EntityStore, addressBook: AddressBook) {
    val entityId = Id.decode(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))

    val entity = getOrCopyEntity(authority.authorityId, entityStore, entityId)
        ?: throw IllegalArgumentException("Unable to save unknown entity: $entityId")

    entityStore.updateEntities(entity)
    getEntityResults(authority, entityStore, addressBook, setOf(entityId))
        .firstOrNull()
        .nullOrElse { call.respond(it) }
}

fun newResourceFromUrl(authority: Authority, entityStore: EntityStore, addressBook: AddressBook, url: String) : EntityResult? {
    try {
        val entity = getOrCopyEntity(authority.authorityId, entityStore, Id.ofUri(URI(url)))
            ?: run {
                // TODO: What if URL isn't html?
                val parser = HtmlParser(httpClient.get(url))
                val resource = ResourceEntity(
                    authority.authorityId,
                    URI(url),
                    parser.parseTitle(),
                    parser.parseDescription(),
                    null, // TODO: Grab proper text
                    parser.parseImageUri()
                )
                entityStore.updateEntities(resource)
                resource
            }

        return getEntityResult(authority, entityStore, addressBook, entity.entityId)
    }catch (e: Exception){
        logger.error { e }
    }

    return null
}

fun newPost(authority: Authority, entityStore: EntityStore, addressBook: AddressBook, entityPayload: EntityPayload): EntityResult? {
    val url = entityPayload.description?.trim()

    if(url != null && urlRegex.matchEntire(url) != null) {
        val result = newResourceFromUrl(authority, entityStore, addressBook, url)
        if(result != null)
            return result
    }

    return updateEntity(authority, entityStore, addressBook, PostEntity(authority.authorityId), entityPayload)
}

suspend fun newPost(call: ApplicationCall, authority: Authority, entityStore: EntityStore, addressBook: AddressBook) {
    val entityPayload = call.receive<EntityPayload>()
    newPost(authority, entityStore, addressBook, entityPayload).nullOrElse { call.respond(it) }
}