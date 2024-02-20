/*
 * Copyright 2024 OpenCola
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

package io.opencola.storage.cache

import mu.KotlinLogging
import io.opencola.content.parseMime
import io.opencola.content.splitMht
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import io.opencola.storage.entitystore.EntityStore
import io.opencola.storage.filestore.ContentAddressedFileStore
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

// Class used to server mhtl data, since it can't be served directly to a browser. 
// Used for serving saved copies of web pages
class MhtCache(private val cachePath: Path, private val entityStore: EntityStore, private val fileStore: ContentAddressedFileStore) {
    private val logger = KotlinLogging.logger("MhtCache")

    init{
        logger.info { "Cache Path: $cachePath" }
        if(!cachePath.exists()) cachePath.createDirectory()
    }

    // TODO: Move this to entityService, since it's general
    private fun getDataEntity(entityId: Id, authorityId: Id? = null): DataEntity? {
        val entity = authorityId?.let { entityStore.getEntity(authorityId, entityId) }
            ?: entityStore.getEntities(emptySet(), setOf(entityId)).filterIsInstance<DataEntity>().firstOrNull()

        return when (entity) {
            // TODO: This grabs an arbitrary dataId. Probably should grab most recent
            is ResourceEntity -> entity.dataIds.let { entityStore.getEntity(authorityId!!, it.first()) }
            is DataEntity -> entity
            else -> null
        } as DataEntity?
    }

    fun getData(entityId: Id, authorityId: Id? = null): ByteArray? {
        return getDataEntity(entityId, authorityId)?.let { fileStore.read(it.entityId)  }
    }

    private fun cachedPartPath(id: Id, partName: String): Path {
        return cachePath.resolve(id.toString()).resolve(partName)
    }

    private fun createDataDirectory(id: Id){
        val dataPath = cachePath.resolve(id.toString())
        if(!dataPath.exists()){
            dataPath.createDirectory()
        }
    }

    private fun cacheMhtParts(entityId: Id, authorityId: Id? = null){
        val data = getData(entityId, authorityId)

        if(data == null){
            logger.warn { "No data available to cache for id: $entityId" }
            return
        }

        val message = parseMime(ByteArrayInputStream(data))
        createDataDirectory(entityId)

        if(message != null) {
            val parts = splitMht(message)

            parts.forEach{
                cachedPartPath(entityId, it.name).outputStream().use{ stream ->
                    stream.write(it.bytes)
                }
            }
        }
    }

    private fun isPartCached(id: Id, partName: String): Boolean {
        return cachePath.resolve(id.toString()).resolve(partName).exists()
    }

    private fun getCachedPart(id: Id, partName: String): ByteArray? {
        val partPath = cachedPartPath(id, partName)
        return if(partPath.exists()) partPath.readBytes() else null
    }

    fun getDataPart(authorityId: Id?, entityId: Id, partName: String): ByteArray? {
        if(!isPartCached(entityId, partName)){
            cacheMhtParts(entityId, authorityId)
        }

        return getCachedPart(entityId, partName)
    }
}