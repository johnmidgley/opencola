package io.opencola.search

import io.opencola.application.TestApplication
import io.opencola.model.Id
import org.junit.Test

class LuceneSearchIndexTest {
    private val authorityId = Id.ofData("authority".toByteArray())
    private val luceneSearchIndex = LuceneSearchIndex(TestApplication.storagePath.resolve("lucene"))

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

    @Test
    fun testLucenePaging(){
        testPaging(luceneSearchIndex)
    }
}