package opencola.server.handlers

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import opencola.core.content.MhtmlPage
import opencola.core.content.TextExtractor
import opencola.core.content.parseMhtml
import opencola.core.extensions.nullOrElse
import opencola.core.model.*
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.io.ByteArrayOutputStream
import java.net.URI

fun updateResource(authorityId: Id, entityStore: EntityStore, fileStore: FileStore, textExtractor: TextExtractor,
                   mhtmlPage: MhtmlPage, actions: Actions): ResourceEntity {
    val writer = DefaultMessageWriter()
    ByteArrayOutputStream().use { outputStream ->
        writer.writeMessage(mhtmlPage.message, outputStream)
        val pageBytes = outputStream.toByteArray()
        val dataId = fileStore.write(pageBytes)
        val mimeType = textExtractor.getType(pageBytes)
        val resourceId = Id.ofUri(mhtmlPage.uri)
        val entity = (entityStore.getEntity(authorityId, resourceId) ?: ResourceEntity(authorityId, mhtmlPage.uri)) as ResourceEntity

        // Add / update fields
        // TODO - Check if setting null writes a retraction when fields are null
        entity.dataId = entity.dataId.plus(dataId)
        entity.name = mhtmlPage.title
        entity.text = mhtmlPage.text
        entity.description = mhtmlPage.description
        entity.imageUri = mhtmlPage.imageUri

        actions.trust.nullOrElse { entity.trust = it }
        actions.like.nullOrElse { entity.like = it }
        actions.rating.nullOrElse { entity.rating = it }

        val dataEntity = (entityStore.getEntity(authorityId, dataId) ?: DataEntity(
            authorityId,
            dataId,
            mimeType
        ))

        entityStore.updateEntities(entity, dataEntity)
        return entity
    }
}

fun handleAction(authorityId: Id, entityStore: EntityStore, fileStore: FileStore, textExtractor: TextExtractor,
                 action: String, value: String?, mhtml: ByteArray) {
    val mhtmlPage = mhtml.inputStream().use { parseMhtml(it) ?: throw RuntimeException("Unable to parse mhtml") }

    val actions = when (action) {
        "save" -> Actions(save = true)
        "like" -> Actions(like = value?.toBooleanStrict() ?: throw RuntimeException("No value specified for like"))
        "trust" -> Actions(trust = value?.toFloat() ?: throw RuntimeException("No value specified for trust"))
        else -> throw NotImplementedError("No handler for $action")
    }

    updateResource(authorityId, entityStore, fileStore, textExtractor,  mhtmlPage, actions)
}

suspend fun handlePostActionCall(call: ApplicationCall,
                                 authorityId: Id,
                                 entityStore: EntityStore,
                                 fileStore: FileStore,
                                 textExtractor: TextExtractor) {
    val multipart = call.receiveMultipart()
    var action: String? = null
    var value: String? = null
    var mhtml: ByteArray? = null

    multipart.forEachPart { part ->
        when (part) {
            is PartData.FormItem -> {
                when (part.name) {
                    "action" -> action = part.value
                    "value" -> value = part.value
                    else -> throw IllegalArgumentException("Unknown FormItem in action request: ${part.name}")
                }
            }
            is PartData.FileItem -> {
                if (part.name != "mhtml") throw IllegalArgumentException("Unknown FileItem in action request: ${part.name}")
                mhtml = part.streamProvider().use { it.readAllBytes() }
            }
            else -> throw IllegalArgumentException("Unknown part in request: ${part.name}")
        }
    }

    if (action == null) {
        throw IllegalArgumentException("No action specified for request")
    }

    if (value == null) {
        throw IllegalArgumentException("No value specified for request")
    }

    if (mhtml == null) {
        throw IllegalArgumentException("No mhtml specified for request")
    }

    handleAction(authorityId, entityStore, fileStore, textExtractor, action as String, value, mhtml as ByteArray)
    call.respond(HttpStatusCode.Accepted)
}

suspend fun handleGetActionsCall(call: ApplicationCall, authorityId: Id, entityStore: EntityStore) {
    val stringUri = call.parameters["uri"] ?: throw IllegalArgumentException("No uri set")
    val entityId = Id.ofUri(URI(stringUri))
    val entity = entityStore.getEntity(authorityId, entityId)

    if (entity != null) {
        call.respond(Actions(true, entity.trust, entity.like, entity.rating))
    }
}