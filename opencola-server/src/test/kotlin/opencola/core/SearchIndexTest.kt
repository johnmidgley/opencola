import opencola.core.content.MhtmlPage
import opencola.core.content.TextExtractor
import opencola.core.content.parseMime
import opencola.core.extensions.nullOrElse
import opencola.core.model.ResourceEntity
import opencola.core.search.SearchIndex
import org.junit.Test
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.assertEquals

class SearchIndexTest {
    private val authority = getAuthority()
    private val searchIndex = SearchIndex(authority)

    init {
        // Make sure we have a fresh index
        searchIndex.delete()
        // This is horrible, but there's no way to know when solr is ready to create the index again other than trying to create the index
        // TODO: Likely a race condition here between deleting the old index and creating a new one
        // TODO: Think about overriding index name, so that fresh index can be used. Or just generate new authority for each version of this test.
        Thread.sleep(1000)
        searchIndex.create()

    }
    // @Test
    fun testCreateIndex(){
        val searchService = SearchIndex(getAuthority())
        searchService.create()
    }

    // @Test
    fun testDeleteIndex(){
        val searchService = SearchIndex(getAuthority())
        searchService.delete()
    }

    @Test
    fun testIndex(){
        val keyword = "keyword"
        val resourceEntity = ResourceEntity(authority.entityId, URI("http://www.site.com/page"), description = "Test description with $keyword")
        searchIndex.index(resourceEntity)

        val results = searchIndex.search(keyword)
        assertEquals(1, results.size)
        assertEquals(resourceEntity.description, results[0].description)
    }

    @Test
    fun testIndexResourceWithMhtml(){
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()

        val path = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
        val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
        val mhtmlPage = MhtmlPage(message)

        val textExtractor = TextExtractor()
        val text = mhtmlPage.htmlText.nullOrElse { textExtractor.getBody(it.toByteArray()) }
        val resourceEntity = ResourceEntity(authority.entityId, mhtmlPage.uri, mhtmlPage.title, text = text)

        searchIndex.index(resourceEntity)
        val results = searchIndex.search("game of life")
        assertEquals(1, results.size)
        assertEquals(resourceEntity.description, results[0].description)
    }
}