package io.opencola.search

import io.opencola.application.TestApplication
import io.opencola.content.MhtmlPage
import io.opencola.content.TextExtractor
import io.opencola.content.parseMime
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import java.net.URI
import kotlin.io.path.inputStream
import kotlin.test.assertEquals

fun getQuery(queryString: String, authorityIds: Set<Id> = emptySet(), tags: Set<String> = emptySet()): Query {
    return Query(queryString, queryString.split(" "), authorityIds, tags)
}

fun testIndex(authorityId: Id, searchIndex: SearchIndex) {
    val keyword = "keyword"
    val resourceEntity =
        ResourceEntity(authorityId, URI("https://www.site.com/page"), description = "Test description with $keyword")
    searchIndex.addEntities(resourceEntity)

    val results = searchIndex.getAllResults(getQuery(keyword)).toList()
    assertEquals(1, results.size)
    assertEquals(resourceEntity.authorityId, results[0].authorityId)
    assertEquals(resourceEntity.entityId, results[0].entityId)
    assertEquals(resourceEntity.name, results[0].name)
    assertEquals(resourceEntity.description, results[0].description)
}

fun indexGameOfLife(authorityId: Id, searchIndex: SearchIndex): ResourceEntity {
    val path = TestApplication.projectHome.resolve("sample-docs/Conway's Game of Life - Wikipedia.mht")
    val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
    val mhtmlPage = MhtmlPage(message)

    val textExtractor = TextExtractor()
    val text = mhtmlPage.htmlText?.let { textExtractor.getBody(it.toByteArray()) }
    val resourceEntity = ResourceEntity(authorityId, mhtmlPage.uri, mhtmlPage.title, text = text)

    searchIndex.addEntities(resourceEntity)
    return resourceEntity
}

fun testIndexResourceWithMhtml(authorityId: Id, searchIndex: SearchIndex) {
    val resourceEntity = indexGameOfLife(authorityId, searchIndex)
    val results = searchIndex.getAllResults(getQuery("game of life")).toList()
    assertEquals(1, results.size)
    assertEquals(resourceEntity.description, results[0].description)
}

fun testRepeatIndexing(authorityId: Id, searchIndex: SearchIndex) {
    indexGameOfLife(authorityId, searchIndex)
    val resourceEntity = indexGameOfLife(authorityId, searchIndex)

    val results = searchIndex.getAllResults(getQuery("game of life"), 100).toList()
    assertEquals(1, results.size)
    assertEquals(resourceEntity.description, results[0].description)
}

fun testPaging(searchIndex: SearchIndex) {
    val authorityId = Id.ofData("testPaging".toByteArray())
    val addedResources = (1..20).map { n ->
        val resourceEntity =
            ResourceEntity(authorityId, URI("https://www.site.com/page/$n"), description = "testPaging $n")
        resourceEntity.also { searchIndex.addEntities(it) }
    }

    fun getDocNumber(description: String) = description.split(" ").last().toInt()
    val sourceDocNumbers = addedResources.map { getDocNumber(it.description!!) }.toSet()
    val destDocNumbers = searchIndex.getAllResults(getQuery("testPaging", setOf(authorityId)), 5)
        .map { getDocNumber(it.description!!) }
        .toSet()

    assertEquals(sourceDocNumbers, destDocNumbers)
}

fun testComponentSearch(searchIndex: SearchIndex) {
    val authorityId0 = Id.ofData("testComponentSearch0".toByteArray())
    val authorityId1 = Id.ofData("testComponentSearch1".toByteArray())
    val tag0 = "tag0"
    val tag1 = "tag1"
    val commonTerm = "commonTerm"
    val description0 = "testComponentSearch0 $commonTerm"
    val resource0 =
        ResourceEntity(
            authorityId0,
            URI("https://www.site.com/0"),
            description = description0,
            tags = listOf(tag0)
        )

    val description1 = "testComponentSearch1 $commonTerm"
    val resource1 = ResourceEntity(
        authorityId1,
        URI("https://www.site.com/1"),
        description = description1,
        tags = listOf(tag0, tag1)
    )
    val description2 = "testComponentSearch2"
    val resource2 = ResourceEntity(
        authorityId1,
        URI("https://www.site.com/2"),
        description = description2,
    )

    searchIndex.addEntities(resource0, resource1, resource2)

    // Test term only matching single doc
    val results1 = searchIndex.getAllResults(getQuery(description0)).toList()
    assertEquals(1, results1.size)
    assertEquals(authorityId0, results1[0].authorityId)
    assertEquals(resource0.entityId, results1[0].entityId)
    assertEquals(resource0.description, results1[0].description)

    // Test term only matching multiple docs
    val results2 = searchIndex.getAllResults(getQuery(commonTerm)).toList()
    assertEquals(2, results2.size)
    assertEquals(authorityId0, results2[0].authorityId)
    assertEquals(resource0.entityId, results2[0].entityId)
    assertEquals(resource0.description, results2[0].description)
    assertEquals(authorityId1, results2[1].authorityId)
    assertEquals(resource1.entityId, results2[1].entityId)
    assertEquals(resource1.description, results2[1].description)

    // Test tag matching single doc
    val results3 = searchIndex.getAllResults(getQuery("", tags = setOf(tag1))).toList()
    assertEquals(1, results3.size)
    assertEquals(authorityId1, results3[0].authorityId)
    assertEquals(resource1.entityId, results3[0].entityId)
    assertEquals(resource1.description, results3[0].description)

    // Test tag matching multiple docs
    val results4 = searchIndex.getAllResults(getQuery("", tags = setOf(tag0))).toList()
    assertEquals(2, results4.size)
    assertEquals(authorityId0, results4[0].authorityId)
    assertEquals(resource0.entityId, results4[0].entityId)
    assertEquals(resource0.description, results4[0].description)
    assertEquals(authorityId1, results4[1].authorityId)
    assertEquals(resource1.entityId, results4[1].entityId)
    assertEquals(resource1.description, results4[1].description)

    // Test authority matching single doc
    val results5 = searchIndex.getAllResults(getQuery("", setOf(authorityId0))).toList()
    assertEquals(1, results5.size)
    assertEquals(authorityId0, results5[0].authorityId)
    assertEquals(resource0.entityId, results5[0].entityId)
    assertEquals(resource0.description, results5[0].description)

    // Test authority matching multiple docs
    val results6 = searchIndex.getAllResults(getQuery("", setOf(authorityId1))).toList()
    assertEquals(2, results6.size)
    assertEquals(authorityId1, results6[0].authorityId)
    assertEquals(resource1.entityId, results6[0].entityId)
    assertEquals(resource1.description, results6[0].description)
    assertEquals(authorityId1, results6[1].authorityId)
    assertEquals(resource2.entityId, results6[1].entityId)
    assertEquals(resource2.description, results6[1].description)

    // Test authority and tag matching single doc
    val results7 = searchIndex.getAllResults(getQuery("", setOf(authorityId0), setOf(tag0))).toList()
    assertEquals(1, results7.size)
    assertEquals(authorityId0, results7[0].authorityId)
    assertEquals(resource0.entityId, results7[0].entityId)
    assertEquals(resource0.description, results7[0].description)

    // Test term, authority and tag matching single doc
    val results8 = searchIndex.getAllResults(getQuery(description1, setOf(authorityId1), setOf(tag0))).toList()
    assertEquals(1, results8.size)
    assertEquals(authorityId1, results8[0].authorityId)
    assertEquals(resource1.entityId, results8[0].entityId)
    assertEquals(resource1.description, results8[0].description)

    // Test term authority and tag search with no matches
    val results9 = searchIndex.getAllResults(getQuery(commonTerm, setOf(authorityId0), setOf(tag1))).toList()
    assertEquals(0, results9.size)
}

fun testDeleteEntity(searchIndex: SearchIndex) {
    val authorityId = Id.new()
    val keyword = "keyword"
    val resourceEntity =
        ResourceEntity(authorityId, URI("https://www.site.com/page"), description = "Test description with $keyword")
    searchIndex.addEntities(resourceEntity)

    val results = searchIndex.getAllResults(getQuery(keyword)).toList()
    assertEquals(1, results.size)
    assertEquals(resourceEntity.authorityId, results[0].authorityId)
    assertEquals(resourceEntity.entityId, results[0].entityId)
    assertEquals(resourceEntity.name, results[0].name)
    assertEquals(resourceEntity.description, results[0].description)

    searchIndex.deleteEntities(authorityId, resourceEntity.entityId)

    val results2 = searchIndex.getAllResults(getQuery(keyword)).toList()
    assertEquals(0, results2.size)
}
