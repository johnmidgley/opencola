package opencola.core.storage

import opencola.core.TestApplication
import opencola.core.content.MhtmlPage
import opencola.core.content.parseMime
import opencola.core.model.Actions
import opencola.core.model.Authority
import opencola.service.EntityService
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
        val entityService by injector.instance<EntityService>()
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()

        val path = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
        val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
        val mhtmlPage = MhtmlPage(message)

        val entity = entityService.updateResource(mhtmlPage, Actions())
        val data = mhtCache.getData(authority.authorityId, entity.entityId)
        assertNotNull(data)

        val part0 = mhtCache.getDataPart(authority.authorityId, entity.entityId, "0.html")
        assertNotNull(part0)
    }
}