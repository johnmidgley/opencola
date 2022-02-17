package opencola.server

import mu.KotlinLogging
import opencola.core.content.parseMime
import opencola.core.content.splitMht
import opencola.core.extensions.nullOrElse
import opencola.core.model.Authority
import opencola.core.model.DataEntity
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import opencola.core.storage.EntityStore
import opencola.core.storage.FileStore
import java.io.ByteArrayInputStream
import java.nio.file.Path
import kotlin.io.path.*

import opencola.core.config.Application.Global.instance as app

// TODO - Move to DataService.
// TODO- Factor out MhtCache
class DataHandler(private val authority: Authority, private val entityStore: EntityStore, private val fileStore: FileStore) {
    private val logger = KotlinLogging.logger {}

    // TODO: Add cache to config
    private val mhtCachePath = app.storagePath.resolve("mht-cache")

    init{
        if(!mhtCachePath.exists()) mhtCachePath.createDirectory()
    }

    private fun getDataEntity(id: Id): DataEntity? {
        val entity = entityStore.getEntity(authority, id)

       return when (entity) {
            is ResourceEntity -> entity.dataId.nullOrElse { entityStore.getEntity(authority, it) }
            is DataEntity -> entity
            else -> null
        } as DataEntity?
    }

    fun getData(id: Id): ByteArray? {
        return getDataEntity(id).nullOrElse { fileStore.read(it.entityId)  }
    }

    private fun cachedPartPath(id: Id, partName: String): Path {
        return mhtCachePath.resolve(id.toString()).resolve(partName)
    }

    fun createDataDirectory(id: Id){
        val dataPath = mhtCachePath.resolve(id.toString())
        if(!dataPath.exists()){
            dataPath.createDirectory()
        }
    }

    private fun cacheMhtParts(id: Id){
        val data = getData(id)

        if(data == null){
            logger.warn { "No data available to cache for id: $id" }
            return
        }

        val message = parseMime(ByteArrayInputStream(data))
        createDataDirectory(id)

        if(message != null) {
            val parts = splitMht(message)

            parts.forEach{
                cachedPartPath(id, it.name).outputStream().use{ stream ->
                    stream.write(it.bytes)
                }
            }
        }
    }

    private fun isPartCached(id: Id, partName: String): Boolean {
        return mhtCachePath.resolve(id.toString()).resolve(partName).exists()
    }

    private fun getCachedPart(id: Id, partName: String): ByteArray? {
        val partPath = cachedPartPath(id, partName)
        return if(partPath.exists()) partPath.readBytes() else null
    }

    fun getDataPart(id: Id, partName: String): ByteArray? {
        if(!isPartCached(id, partName)){
            cacheMhtParts(id)
        }

        return getCachedPart(id, partName)
    }
}