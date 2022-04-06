package opencola.core.search

import opencola.core.TestApplication
import opencola.core.model.Authority
import org.junit.Test
import org.kodein.di.instance

class SolrSearchIndexTest {
    private val authority by TestApplication.instance.injector.instance<Authority>()
    private val searchConfig = TestApplication.config.search
    private val solrSearchIndex = SolrSearchIndex(authority.authorityId, searchConfig)

    init {
        // Make sure we have a fresh index
        solrSearchIndex.delete()
        // This is horrible, but there's no way to know when solr is ready to create the index again other than trying to create the index
        // TODO: Likely a race condition here between deleting the old index and creating a new one
        // TODO: Think about overriding index name, so that fresh index can be used. Or just generate new authority for each version of this test.
        Thread.sleep(1000)
        solrSearchIndex.create()

    }

    @Test
    fun testIndexSolr(){
        testIndex(authority.authorityId, solrSearchIndex)

    }

    @Test
    fun testSolrIndexResourceWithMhtml(){
        testIndexResourceWithMhtml(authority.authorityId, solrSearchIndex)
    }
}