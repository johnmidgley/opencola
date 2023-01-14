package opencola.core.search

import opencola.core.TestApplication
import io.opencola.model.Id
import io.opencola.search.LuceneSearchIndex
import org.junit.Test

class LuceneSearchIndexTest {
    private val authorityId = Id.ofData("authority".toByteArray())
    private val luceneSearchIndex = LuceneSearchIndex(authorityId, TestApplication.storagePath.resolve("lucene"))

    @Test
    fun testIndexLucene(){
        testIndex(authorityId, luceneSearchIndex)
    }

    @Test
    fun testLuceneIndexResourceWithMhtml(){
        testIndexResourceWithMhtml(authorityId, luceneSearchIndex)
    }

    @Test
    fun testLuceneRepeatIndexing(){
        testRepeatIndexing(authorityId, luceneSearchIndex)
    }
}