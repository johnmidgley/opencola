package io.opencola.core.storage

import mu.KotlinLogging
import io.opencola.core.content.parseMime
import io.opencola.core.content.splitMht
import io.opencola.core.extensions.nullOrElse
import io.opencola.model.DataEntity
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

class MhtCache(private val cachePath: Path, private val entityStore: EntityStore, private val fileStore: FileStore) {
    private val logger = KotlinLogging.logger("MhtCache")

    init{
        logger.info { "Cache Path: $cachePath" }
        if(!cachePath.exists()) cachePath.createDirectory()
    }

    // TODO: Move this to entityService, since it's general
    private fun getDataEntity(authorityId: Id, entityId: Id): DataEntity? {
        val entity = entityStore.getEntity(authorityId, entityId)

        return when (entity) {
            // TODO: This grabs an arbitrary dataId. Probably should grab most recent
            is ResourceEntity -> entity.dataId.nullOrElse { entityStore.getEntity(authorityId, it.first()) }
            is DataEntity -> entity
            else -> null
        } as DataEntity?
    }

    fun getData(authorityId: Id, entityId: Id): ByteArray? {
        return getDataEntity(authorityId, entityId).nullOrElse { fileStore.read(it.entityId)  }
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

    private fun cacheMhtParts(authorityId: Id, entityId: Id){
        val data = getData(authorityId, entityId)

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

    fun getDataPart(authorityId: Id, entityId: Id, partName: String): ByteArray? {
        if(!isPartCached(entityId, partName)){
            cacheMhtParts(authorityId, entityId)
        }

        return getCachedPart(entityId, partName)
    }
}