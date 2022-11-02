package opencola.server.handlers

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.opencola.core.model.DataEntity
import io.opencola.core.model.Id
import io.opencola.core.network.response
import io.opencola.core.storage.EntityStore
import io.opencola.core.storage.FileStore
import mu.KotlinLogging
import io.opencola.core.storage.MhtCache

private val logger = KotlinLogging.logger("DataHandler")

suspend fun handleGetDataCall(call: ApplicationCall, entityStore: EntityStore, fileStore: FileStore, authorityId: Id) {
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val entityId = Id.decode(stringId)

    val dataEntity = entityStore.getEntity(authorityId, Id.decode(stringId)) as? DataEntity
        ?: throw IllegalArgumentException("Unknown data entity: $stringId")

    logger.info { "MimeType: ${dataEntity.mimeType}" }

    if(dataEntity.mimeType == "multipart/related") {
        call.respondRedirect("$stringId/0.html")
        return
    }

    val data = fileStore.read(dataEntity.entityId)

    if (data == null) {
        call.respondText(status = HttpStatusCode.NoContent) { "No data for id: $entityId" }
    } else {
        call.response.headers.append("content-type", dataEntity.mimeType ?: "application/octet-stream")
        call.respondBytes(data)
    }
}

suspend fun handleGetDataPartCall(call: ApplicationCall, authorityId: Id, mhtCache: MhtCache) {
    // TODO: All handlers need to wrap like this?
    try {
        val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
        val partName = call.parameters["partName"] ?: throw IllegalArgumentException("No partName set")

        val bytes = mhtCache.getDataPart(authorityId, Id.decode(stringId), partName)
        if (bytes != null) {
            val contentType = ContentType.fromFilePath(partName).firstOrNull()
            call.respondBytes(bytes, contentType = contentType)
        }
    } catch (e: Exception) {
        logger.error { e }
    }
}