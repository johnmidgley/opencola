package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.model.Authority
import opencola.core.model.CommentEntity as CommentEntity
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.network.PeerRouter
import opencola.core.storage.EntityStore
import opencola.service.EntityResult
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

suspend fun updateEntity(call: ApplicationCall, authorityId: Id, entityStore: EntityStore){
    val entityItem = call.receive<EntityResult>()
    logger.info { "Updating: $entityItem" }

    val entity = entityStore.getEntity(authorityId, Id.fromHexString(entityItem.entityId)) as? ResourceEntity
    if(entity == null){
        call.respond(HttpStatusCode.Unauthorized)
        return
    }

    val imageUri = URI(entityItem.summary.imageUri)
    if(!imageUri.isAbsolute){
        throw IllegalArgumentException("Image URI must be absolute")
    }

    entity.name = entityItem.summary.name
    entity.imageUri = imageUri
    entity.description = entityItem.summary.description

    entityStore.updateEntities(entity)
    call.respond(HttpStatusCode.OK)
}

@Serializable
data class PostCommentPayload(val commentId: String? = null, val text: String)

suspend fun addComment(call: ApplicationCall, authority: Authority, entityStore: EntityStore, peerRouter: PeerRouter) {
    val authorityId = authority.authorityId
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.fromHexString(stringId)
    val comment = call.receive<PostCommentPayload>()

    logger.info { "Adding comment to $entityId" }

    // TODO: This is interesting. You should be able to comment on an item you don't "own", but what should it do?
    //  Should it automatically add it to your store? Probably...
    val entity = entityStore.getEntity(authorityId, entityId)
        ?: throw IllegalArgumentException("Attempt to add comment to unknown entity")

    val commentEntity =
        if (comment.commentId == null)
            CommentEntity(authorityId, entityId, comment.text)
        else
            entityStore.getEntity(authorityId, Id.fromHexString(comment.commentId)) as? CommentEntity
                ?: throw IllegalArgumentException("Unknown comment: ${comment.commentId}")


    commentEntity.text = comment.text
    entityStore.updateEntities(commentEntity)

    val entityResult = getEntityResults(authority, entityStore, peerRouter, listOf(entityId)).firstOrNull()

    if(entityResult != null)
        call.respond(entityResult)
}
