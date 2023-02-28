package opencola.core.search

import io.opencola.content.MhtmlPage
import io.opencola.content.TextExtractor
import io.opencola.content.parseMime
import io.opencola.util.nullOrElse
import io.opencola.model.Id
import io.opencola.model.ResourceEntity
import io.opencola.search.SearchIndex
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.assertEquals

fun testIndex(authorityId: Id, searchIndex: SearchIndex) {
    val keyword = "keyword"
    val resourceEntity =
        ResourceEntity(authorityId, URI("http://www.site.com/page"), description = "Test description with $keyword")
    searchIndex.add(resourceEntity)

    val results = searchIndex.search(emptySet(), keyword)
    assertEquals(1, results.size)
    assertEquals(resourceEntity.authorityId, results[0].authorityId)
    assertEquals(resourceEntity.entityId, results[0].entityId)
    assertEquals(resourceEntity.name, results[0].name)
    assertEquals(resourceEntity.description, results[0].description)
}

fun indexGameOfLife(authorityId: Id, searchIndex: SearchIndex) : ResourceEntity {
    val rootPath = Path(System.getProperty("user.dir"), "../..", "sample-docs").toString()

    val path = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
    val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
    val mhtmlPage = MhtmlPage(message)

    val textExtractor = TextExtractor()
    val text = mhtmlPage.htmlText.nullOrElse { textExtractor.getBody(it.toByteArray()) }
    val resourceEntity = ResourceEntity(authorityId, mhtmlPage.uri, mhtmlPage.title, text = text)

    searchIndex.add(resourceEntity)
    return resourceEntity
}

fun testIndexResourceWithMhtml(authorityId: Id, searchIndex: SearchIndex) {
    val resourceEntity = indexGameOfLife(authorityId, searchIndex)
    val results = searchIndex.search(emptySet(), "game of life")
    assertEquals(1, results.size)
    assertEquals(resourceEntity.description, results[0].description)
}

fun testRepeatIndexing(authorityId: Id, searchIndex: SearchIndex){
    indexGameOfLife(authorityId, searchIndex)
    val resourceEntity = indexGameOfLife(authorityId, searchIndex)

    val results = searchIndex.search(emptySet(),"game of life")
    assertEquals(1, results.size)
    assertEquals(resourceEntity.description, results[0].description)
}