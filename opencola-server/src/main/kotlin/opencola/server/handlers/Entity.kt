package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.network.PeerRouter
import opencola.core.storage.EntityStore
import java.net.URI

private val logger = KotlinLogging.logger("EntityHandler")

suspend fun getEntity(call: ApplicationCall, authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.fromHexString(stringId)
    val entityResult = getEntityResults(authority, entityStore, peerRouter, listOf(entityId)).firstOrNull()

    if (entityResult != null)
        call.respond(entityResult)
}

// TODO - investigate delete and then re-add. It seems to "restore" all previous saves. Is this good or bad?
suspend fun deleteEntity(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.fromHexString(stringId)

    logger.info { "Deleting $entityId" }
    entityStore.deleteEntity(authorityId, entityId)
    call.respond(HttpStatusCode.OK)
}

@Serializable
data class UpdateEntityPayload(
    val entityId: String,
    val name: String,
    val imageUri: String,
    val description: String,
    val tags: String,
)

suspend fun updateEntity(call: ApplicationCall, authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter){
    val authorityId = authority.authorityId
    val updateEntityPayload = call.receive<UpdateEntityPayload>()
    logger.info { "Updating: $updateEntityPayload" }

    val entity = getOrCopyEntity(authorityId, entityStore, Id.fromHexString(updateEntityPayload.entityId)) as? ResourceEntity
    if(entity == null){
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    val imageUri = updateEntityPayload.imageUri.nullOrElse {
        if(it.isBlank()) null else URI(updateEntityPayload.imageUri)
    }

    if(imageUri != null && !imageUri.isAbsolute){
        throw IllegalArgumentException("Image URI must be absolute")
    }

    entity.name = updateEntityPayload.name
    entity.imageUri = imageUri
    entity.description = updateEntityPayload.description

    val tags = updateEntityPayload.tags.split(" ").filter { it.isNotBlank() }.toSet()
    entity.tags = tags

    entityStore.updateEntities(entity)
    getEntityResult(authority, entityStore, peerRouter, entity.entityId)
        .nullOrElse { call.respond(it) }
}

fun getOrCopyEntity(authorityId : Id, entityStore: EntityStore, entityId: Id): Entity? {
    val existingEntity = entityStore.getEntity(authorityId, entityId) ?:
        entityStore.getEntities(emptyList(), listOf(entityId)).firstOrNull()

    if(existingEntity == null || existingEntity.authorityId == authorityId )
        return existingEntity

    val newEntity = when(existingEntity){
        is ResourceEntity -> {
            ResourceEntity(
                authorityId,
                existingEntity.uri!!,
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

suspend fun addComment(call: ApplicationCall, authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter) {
    val entityId = Id.fromHexString(call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified"))
    val comment = call.receive<PostCommentPayload>()

    addComment(authority, entityStore, entityId, comment.commentId.nullOrElse { Id.fromHexString(it) }, comment.text)
    getEntityResults(authority, entityStore, peerRouter, listOf(entityId))
        .firstOrNull()
        .nullOrElse { call.respond(it) }
}
