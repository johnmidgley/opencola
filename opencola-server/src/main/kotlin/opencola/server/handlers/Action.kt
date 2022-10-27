package opencola.server.handlers

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.opencola.core.content.MhtmlPage
import io.opencola.core.content.TextExtractor
import io.opencola.core.content.parseMhtml
import io.opencola.core.content.toBytes
import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Actions
import io.opencola.core.model.DataEntity
import io.opencola.core.model.Id
import io.opencola.core.model.ResourceEntity
import io.opencola.core.storage.EntityStore
import io.opencola.core.storage.FileStore
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private val logger = KotlinLogging.logger("ActionHandler")

class Content(val mimeType: String, val data: ByteArray)

fun getFileData(uri: URI) : ByteArray? {
    val path = Path(uri.path)

    if(path.exists())
        return path.inputStream().use { it.readAllBytes() }

    return null
}

fun getHttpData(uri: URI): ByteArray? {
    val client = HttpClient(CIO) {
        engine {
            requestTimeout = 30000
        }
    }
    return runBlocking {
        try {
            logger.info { "Getting data for: $uri" }
            val response = client.get(uri.toString())
            logger.info { "Got response for $uri: $response" }
            if (response.status.value < 400) {
                val bytes = response.readBytes()
                logger.info { "Got data for $uri: $response" }
                bytes
            } else {
                logger.error { "Unable to get data for $uri: $response" }
                null
            }
        } catch (e: Exception) {
            logger.error { "Exception during get: $e" }
            null
        }
    }
}

fun getData(uri: URI) : ByteArray? {
    return when (uri.scheme) {
        "file" -> getFileData(uri)
        "http" -> getHttpData(uri)
        "https" -> getHttpData(uri)
        else -> throw IllegalArgumentException("Unable to getData for unknown scheme: ${uri.scheme}")
    }
}

fun getContent(mhtmlPage: MhtmlPage, textExtractor: TextExtractor): Content {
    val data = if(mhtmlPage.embeddedMimeType == null) mhtmlPage.message.toBytes() else getData(mhtmlPage.uri)
        ?: throw IllegalArgumentException("Unable to access data for ${mhtmlPage.uri} ")

    return Content(textExtractor.getType(data), data)
}

fun updatePdfResource(resourceEntity: ResourceEntity, mhtmlPage: MhtmlPage, content: Content) {
    val text = TextExtractor().getBody(content.data)
    resourceEntity.name = Path(mhtmlPage.uri.path).fileName.toString()
    resourceEntity.text = text
    resourceEntity.description = text.substring(0, 500)
    resourceEntity.imageUri = mhtmlPage.imageUri
}

fun updateMultipartResource(resourceEntity: ResourceEntity, mhtmlPage: MhtmlPage) {
    // Add / update fields
    // TODO - Check if setting null writes a retraction when fields are null
    resourceEntity.name = mhtmlPage.title
    resourceEntity.text = mhtmlPage.text
    resourceEntity.description = mhtmlPage.description
    resourceEntity.imageUri = mhtmlPage.imageUri
}

fun getUpdatedResourceEntity(
    authorityId: Id,
    entityStore: EntityStore,
    mhtmlPage: MhtmlPage,
    content: Content,
    actions: Actions
): ResourceEntity {
    // TODO: File URIs are not unique - and only valid on the originating device.
    val resourceId = Id.ofUri(mhtmlPage.uri)
    val entity =
        (entityStore.getEntity(authorityId, resourceId) ?: ResourceEntity(authorityId, mhtmlPage.uri)) as ResourceEntity

    when(content.mimeType) {
        "multipart/related" -> updateMultipartResource(entity, mhtmlPage)
        "application/pdf" -> updatePdfResource(entity, mhtmlPage, content)
        else -> throw IllegalArgumentException("Unhandled content type: ${content.mimeType}")
    }

    actions.trust.nullOrElse { entity.trust = it }
    actions.like.nullOrElse { entity.like = it }
    actions.rating.nullOrElse { entity.rating = it }

    return entity
}

fun updateResource(
    authorityId: Id, entityStore: EntityStore, fileStore: FileStore, textExtractor: TextExtractor,
    mhtmlPage: MhtmlPage, actions: Actions
): ResourceEntity {
    val content = getContent(mhtmlPage, textExtractor)
    val entity = getUpdatedResourceEntity(authorityId, entityStore, mhtmlPage, content, actions)

    val dataId = fileStore.write(content.data)
    entity.dataId = entity.dataId.plus(dataId)
    val dataEntity = (entityStore.getEntity(authorityId, dataId) ?: DataEntity(
        authorityId,
        dataId,
        content.mimeType
    ))

    entityStore.updateEntities(entity, dataEntity)
    return entity
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
                                 textExtractor: TextExtractor
) {
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