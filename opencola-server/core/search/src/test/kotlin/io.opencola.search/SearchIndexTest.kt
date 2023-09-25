package io.opencola.search

import io.opencola.application.TestApplication
import io.opencola.content.MhtmlPage
import io.opencola.content.TextExtractor
import io.opencola.content.parseMime
import io.opencola.util.nullOrElse
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import java.net.URI
import kotlin.io.path.inputStream
import kotlin.test.assertEquals

fun getQuery(queryString: String, authorityIds: Set<Id> = emptySet()): Query {
    return Query(queryString, queryString.split(" "), authorityIds)
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

fun indexGameOfLife(authorityId: Id, searchIndex: SearchIndex) : ResourceEntity {
    val path = TestApplication.projectHome.resolve("sample-docs/Conway's Game of Life - Wikipedia.mht")
    val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
    val mhtmlPage = MhtmlPage(message)

    val textExtractor = TextExtractor()
    val text = mhtmlPage.htmlText.nullOrElse { textExtractor.getBody(it.toByteArray()) }
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

fun testRepeatIndexing(authorityId: Id, searchIndex: SearchIndex){
    indexGameOfLife(authorityId, searchIndex)
    val resourceEntity = indexGameOfLife(authorityId, searchIndex)

    val results = searchIndex.getAllResults(getQuery("game of life"), 100).toList()
    assertEquals(1, results.size)
    assertEquals(resourceEntity.description, results[0].description)
}

fun testPaging(searchIndex: SearchIndex) {
    val authorityId = Id.ofData("testPaging".toByteArray())
    val addedResources = (1..20).map { n ->
        val resourceEntity = ResourceEntity(authorityId, URI("https://www.site.com/page/$n"), description = "testPaging $n")
        resourceEntity.also { searchIndex.addEntities(it) }
    }
    fun getDocNumber(description: String) = description.split(" ").last().toInt()
    val sourceDocNumbers = addedResources.map { getDocNumber(it.description!!) }.toSet()
    val destDocNumbers = searchIndex.getAllResults(getQuery("testPaging", setOf(authorityId)), 5)
        .map { getDocNumber(it.description!!) }
        .toSet()

    assertEquals(sourceDocNumbers, destDocNumbers)
}
