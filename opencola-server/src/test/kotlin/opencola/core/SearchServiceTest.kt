import opencola.core.model.ResourceEntity
import opencola.core.search.SearchService
import org.junit.Test
import java.net.URI
import kotlin.test.assertEquals

class SearchServiceTest {
    private val authority = getAuthority()
    private val searchService = SearchService(authority)

    init {
        // Make sure we have a fresh index
        searchService.deleteIndex()
        // This is horrible, but there's no way to know when solr is ready to create the index again other than trying to create the index
        // TODO: Likely a race condition here between deleting the old index and creating a new one
        // TODO: Think about overriding index name, so that fresh index can be used. Or just generate new authority for each version of this test.
        Thread.sleep(1000)
        searchService.createIndex()

    }
    // @Test
    fun testCreateIndex(){
        val searchService = SearchService(getAuthority())
        searchService.createIndex()
    }

    // @Test
    fun testDeleteIndex(){
        val searchService = SearchService(getAuthority())
        searchService.deleteIndex()
    }

    @Test
    fun testIndex(){
        val keyword = "keyword"
        val resourceEntity = ResourceEntity(authority.entityId, URI("http://www.site.com/page"), description = "Test description with $keyword")
        searchService.index(resourceEntity)

        val results = searchService.search(keyword)
        assertEquals(1, results.size)
        assertEquals(resourceEntity.description, results[0].description)
    }
}