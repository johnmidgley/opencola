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
import io.opencola.content.*
import io.opencola.util.nullOrElse
import io.opencola.model.*
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentBasedFileStore
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream

private val logger = KotlinLogging.logger("ActionHandler")

class Content(val mimeType: String, val data: ByteArray)

fun getFileData(uri: URI): ByteArray? {
    val path = Path(uri.path)

    if (path.exists())
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

fun getData(uri: URI): ByteArray? {
    return when (uri.scheme) {
        "file" -> getFileData(uri)
        "http" -> getHttpData(uri)
        "https" -> getHttpData(uri)
        else -> throw IllegalArgumentException("Unable to getData for unknown scheme: ${uri.scheme}")
    }
}

fun getContent(mhtmlPage: MhtmlPage): Content {
    val embeddedMimeType = mhtmlPage.embeddedMimeType
    return if (embeddedMimeType == null) {
        val parts = splitMht(mhtmlPage.message)
        if (parts.count() == 1) {
            // Not really multipart (e.g. image) - unwrap so content is directly accessible.
            parts.first().let { Content(it.mimeType, it.bytes) }
        } else
            Content("multipart/related", mhtmlPage.message.toBytes())
    } else {
        val data = getData(mhtmlPage.uri)
            ?: throw RuntimeException("Unable to access data for ${mhtmlPage.uri} ")
        Content(embeddedMimeType, data)
    }
}

fun getImageFromPdf(content: Content): Content? {
    return getFirstImageFromPDF(content.data)?.let { image ->
        Content("image/png", image.toBytes("PNG"))
    }
}

fun updatePdfResource(
    resourceEntity: ResourceEntity,
    mhtmlPage: MhtmlPage,
    content: Content,
//    entityStore: EntityStore,
//    fileStore: FileStore
): List<Entity> {
    val text = TextExtractor().getBody(content.data)
    // TODO: Re-enable image extraction when image cache is working
//    val imageDataEntity = getImageFromPdf(content)?.let { imageContent ->
//        getDataEntity(resourceEntity.authorityId, entityStore, fileStore, imageContent) }

    resourceEntity.name = Path(mhtmlPage.uri.path).fileName.toString()
    resourceEntity.text = text
    resourceEntity.description = text.substring(0, 500)
    // resourceEntity.imageUri = imageDataEntity?.let { URI("data/${it.entityId}") }

    // return imageDataEntity?.let { listOf(it) } ?: emptyList()
    return emptyList()
}

fun updateMultipartResource(resourceEntity: ResourceEntity, mhtmlPage: MhtmlPage): List<Entity> {
    // Add / update fields
    // TODO - Check if setting null writes a retraction when fields are null
    resourceEntity.name = mhtmlPage.title
    resourceEntity.text = mhtmlPage.text
    resourceEntity.description = mhtmlPage.description
    resourceEntity.imageUri = mhtmlPage.imageUri

    return emptyList()
}

fun getDataEntity(authorityId: Id, entityStore: EntityStore, fileStore: ContentBasedFileStore, content: Content): DataEntity {
    val dataId = fileStore.write(content.data)
    return (entityStore.getEntity(authorityId, dataId) ?: DataEntity(
        authorityId,
        dataId,
        content.mimeType
    )) as DataEntity
}

fun updateActions(entity: Entity, actions: Actions) {
    actions.trust.nullOrElse { entity.trust = it }
    actions.like.nullOrElse { entity.like = it }
    actions.rating.nullOrElse { entity.rating = it }
}

fun updateResource(
    authorityId: Id,
    entityStore: EntityStore,
    fileStore: ContentBasedFileStore,
    mhtmlPage: MhtmlPage,
    actions: Actions
): ResourceEntity {
    // TODO: File URIs are not unique - and only valid on the originating device.
    val content = getContent(mhtmlPage)
    val dataEntity = getDataEntity(authorityId, entityStore, fileStore, content)
    val entity = (entityStore.getEntity(authorityId, Id.ofUri(mhtmlPage.uri))
        ?: ResourceEntity(authorityId, mhtmlPage.uri)) as ResourceEntity

    updateActions(entity, actions)
    entity.dataId = entity.dataId.plus(dataEntity.entityId)

    val extraEntities = when (content.mimeType) {
        "multipart/related" -> updateMultipartResource(entity, mhtmlPage)
        "application/pdf" -> updatePdfResource(entity, mhtmlPage, content)
        else -> updateMultipartResource(entity, mhtmlPage)
    }

    entityStore.updateEntities(entity, dataEntity, *extraEntities.toTypedArray())
    return entity
}

fun handleAction(
    authorityId: Id,
    entityStore: EntityStore,
    fileStore: ContentBasedFileStore,
    action: String,
    value: String?,
    mhtml: ByteArray
) {
    val mhtmlPage = mhtml.inputStream().use { parseMhtml(it) ?: throw RuntimeException("Unable to parse mhtml") }

    val actions = when (action) {
        "save" -> Actions(save = true)
        "like" -> Actions(like = value?.toBooleanStrict() ?: throw RuntimeException("No value specified for like"))
        "trust" -> Actions(trust = value?.toFloat() ?: throw RuntimeException("No value specified for trust"))
        else -> throw NotImplementedError("No handler for $action")
    }

    updateResource(authorityId, entityStore, fileStore, mhtmlPage, actions)
}

suspend fun handlePostActionCall(
    call: ApplicationCall,
    authorityId: Id,
    entityStore: EntityStore,
    fileStore: ContentBasedFileStore,
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

    handleAction(authorityId, entityStore, fileStore, action as String, value, mhtml as ByteArray)
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