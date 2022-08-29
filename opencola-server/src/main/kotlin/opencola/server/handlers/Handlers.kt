package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import opencola.core.event.EventBus
import opencola.core.event.Events
import opencola.core.model.*
import opencola.core.network.Notification
import opencola.core.storage.AddressBook
import opencola.core.storage.EntityStore
import opencola.core.storage.EntityStore.TransactionOrder
import opencola.core.storage.MhtCache
import opencola.core.search.SearchService

private val logger = KotlinLogging.logger("Handler")

suspend fun handleGetSearchCall(call: ApplicationCall, searchService: SearchService) {
    val query =
        call.request.queryParameters["q"] ?: throw IllegalArgumentException("No query (q) specified in parameters")
    call.respond(searchService.search(query))
}

suspend fun handleGetDataCall(call: ApplicationCall, mhtCache: MhtCache, authorityId: Id) {
    val stringId = call.parameters["id"] ?: throw IllegalArgumentException("No id set")
    val entityId = Id.decode(stringId)

    val data = mhtCache.getData(authorityId, entityId)

    if (data == null) {
        call.respondText(status = HttpStatusCode.NoContent) { "No data for id: $entityId" }
    } else {
        call.respondBytes(data, ContentType.Application.OctetStream)
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