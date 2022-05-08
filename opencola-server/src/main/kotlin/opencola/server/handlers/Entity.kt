package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import mu.KotlinLogging
import opencola.core.model.CommentEntity as CommentEntity
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.service.EntityResult
import java.net.URI

private val logger = KotlinLogging.logger("EntityHandler")

data class Comment(val commentId: String?, val entityId: String, val text: String)

suspend fun getEntity(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entity = entityStore.getEntity(authorityId, Id.fromHexString(stringId))

    if (entity != null)
        call.respond(entity.getAllFacts())
}

suspend fun getEntity(call: ApplicationCall, entityStore: EntityStore) {
    val authorityId = Id.fromHexString(
        call.parameters["authorityId"]
            ?: throw IllegalArgumentException("No authority id specified")
    )

    getEntity(call, authorityId, entityStore)
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

suspend fun addComment(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.fromHexString(stringId)
    val comment = call.receive<Comment>()

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
}
