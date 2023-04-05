package opencola.server.handlers

import io.ktor.http.content.*
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.storage.EntityStore
import io.opencola.storage.FileStore
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger("UploadHandler")

@Serializable
class UploadItem(val id: String, val name: String?, val type: String?)

@Serializable
class UploadItems(val items: List<UploadItem>)

fun createDataEntity(entityStore: EntityStore, personaId: Id, dataId: Id, name: String?,  type: String): DataEntity {
    return entityStore.getEntity(personaId, dataId) as? DataEntity
        ?: DataEntity(personaId, dataId, type, name).let { entityStore.updateEntities(it); it}
}

suspend fun handleUpload(entityStore: EntityStore,
                         fileStore: FileStore,
                         personaId: Id,
                         multipart: MultiPartData): UploadItems {
    val items = mutableListOf<UploadItem>()

    multipart.forEachPart { part ->
        if (part is PartData.FileItem) {
            logger.info("Handling upload part: name='${part.originalFileName}' type='${part.contentType}'")
            part.streamProvider().use { its ->
                val dataId = fileStore.write(its)
                createDataEntity(entityStore, personaId, dataId, part.originalFileName, part.contentType.toString())
                items.add(UploadItem(dataId.toString(), part.originalFileName, part.contentType.toString()))
            }
        }
        part.dispose()
    }

    return UploadItems(items)
}