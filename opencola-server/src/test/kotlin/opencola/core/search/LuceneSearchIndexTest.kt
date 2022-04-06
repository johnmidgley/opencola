package opencola.core.search

import opencola.core.TestApplication
import opencola.core.model.Id
import org.junit.Test

class LuceneSearchIndexTest {
    private val authorityId = Id.ofData("authority".toByteArray())
    private val luceneSearchIndex = LuceneSearchIndex(authorityId, TestApplication.instance.config.storage.path.resolve("lucene"))

    @Test
    fun testIndexLucene(){
        testIndex(authorityId, luceneSearchIndex)
    }

    @Test
    fun testLuceneIndexResourceWithMhtml(){
        testIndexResourceWithMhtml(authorityId, luceneSearchIndex)
    }
}