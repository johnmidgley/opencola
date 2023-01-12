package opencola.core.storage

import opencola.core.TestApplication
import io.opencola.core.content.MhtmlPage
import io.opencola.core.content.parseMime
import io.opencola.core.model.Actions
import io.opencola.core.model.Authority
import io.opencola.core.storage.EntityStore
import io.opencola.core.storage.FileStore
import io.opencola.core.storage.MhtCache
import opencola.server.handlers.updateResource
import org.junit.Test
import org.kodein.di.instance
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.assertNotNull

class MhtCacheTest {
    val injector = TestApplication.instance.injector

    @Test
    fun testReadFromCache(){
        val authority by injector.instance<Authority>()
        val mhtCache by injector.instance<MhtCache>()
        val entityStore by injector.instance<EntityStore>()
        val fileStore by injector.instance<FileStore>()

        val rootPath = Path(System.getProperty("user.dir"),"../..", "sample-docs").toString()

        val path = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
        val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
        val mhtmlPage = MhtmlPage(message)

        val entity = updateResource(authority.authorityId, entityStore, fileStore, mhtmlPage, Actions())
        val data = mhtCache.getData(authority.authorityId, entity.entityId)
        assertNotNull(data)

        val part0 = mhtCache.getDataPart(authority.authorityId, entity.entityId, "0.html")
        assertNotNull(part0)
    }
}