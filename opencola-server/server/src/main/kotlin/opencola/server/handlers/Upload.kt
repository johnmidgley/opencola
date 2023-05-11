package opencola.server.handlers

import io.ktor.http.content.*
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.FileStore
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger("UploadHandler")

@Serializable
class UploadItem(val id: String, val name: String?, val type: String?)

@Serializable
class UploadItems(val items: List<UploadItem>)

suspend fun getDataEntities(
    entityStore: EntityStore,
    fileStore: FileStore,
    personaId: Id,
    multipart: MultiPartData
): List<DataEntity> {
    val entities = mutableListOf<DataEntity>()

    multipart.forEachPart { part ->
        if (part is PartData.FileItem) {
            logger.info("Handling upload part: name='${part.originalFileName}' type='${part.contentType}'")
            part.streamProvider().use { its ->
                val dataId = fileStore.write(its)
                val dataEntity = entityStore.getEntity(personaId, dataId) as? DataEntity
                    ?: DataEntity(personaId, dataId, part.contentType.toString(), part.originalFileName)
                entities.add(dataEntity)
            }
        }
        part.dispose()
    }

    return entities
}

suspend fun handleUpload(
    entityStore: EntityStore,
    fileStore: FileStore,
    personaId: Id,
    multipart: MultiPartData
): UploadItems {
    val dataEntities = getDataEntities(entityStore, fileStore, personaId, multipart)
    entityStore.updateEntities(*dataEntities.toTypedArray())
    return UploadItems(dataEntities.map { UploadItem(it.entityId.encode(), it.name, it.mimeType) })
}