package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import opencola.core.model.Id
import opencola.core.storage.EntityStore

suspend fun getEntity(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    // TODO: Authority should be passed (and authenticated) in header
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entity = entityStore.getEntity(authorityId, Id.fromHexString(stringId))

    if (entity != null)
        call.respond(entity.getFacts())
}

suspend fun getEntity(call: ApplicationCall, entityStore: EntityStore) {
    val authorityId = Id.fromHexString(
        call.parameters["authorityId"]
            ?: throw IllegalArgumentException("No authority id specified")
    )

    getEntity(call, authorityId, entityStore)
}

suspend fun deleteEntity(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    val stringId = call.parameters["entityId"] ?: throw IllegalArgumentException("No entityId specified")
    val entityId = Id.fromHexString(stringId)
    entityStore.deleteEntity(authorityId, entityId)
    call.respond(HttpStatusCode.OK)
}