/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package opencola.server.handlers

import io.ktor.http.content.*
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import kotlinx.serialization.Serializable
import mu.KotlinLogging

private val logger = KotlinLogging.logger("UploadHandler")

@Serializable
class UploadItem(val id: String, val name: String?, val type: String?)

@Serializable
class UploadItems(val items: List<UploadItem>)

suspend fun getDataEntities(
    entityStore: EntityStore,
    fileStore: ContentAddressedFileStore,
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
    fileStore: ContentAddressedFileStore,
    personaId: Id,
    multipart: MultiPartData
): UploadItems {
    val dataEntities = getDataEntities(entityStore, fileStore, personaId, multipart)
    entityStore.updateEntities(*dataEntities.toTypedArray())
    return UploadItems(dataEntities.map { UploadItem(it.entityId.toString(), it.name, it.mimeType) })
}