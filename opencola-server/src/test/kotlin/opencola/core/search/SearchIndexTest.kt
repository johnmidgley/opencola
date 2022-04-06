package opencola.core.search

import opencola.core.content.MhtmlPage
import opencola.core.content.TextExtractor
import opencola.core.content.parseMime
import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import opencola.core.model.ResourceEntity
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.assertEquals

fun testIndex(authorityId: Id, searchIndex: SearchIndex) {
    val keyword = "keyword"
    val resourceEntity =
        ResourceEntity(authorityId, URI("http://www.site.com/page"), description = "Test description with $keyword")
    searchIndex.index(resourceEntity)

    val results = searchIndex.search(keyword)
    assertEquals(1, results.size)
    assertEquals(resourceEntity.authorityId, results[0].authorityId)
    assertEquals(resourceEntity.entityId, results[0].entityId)
    assertEquals(resourceEntity.name, results[0].name)
    assertEquals(resourceEntity.description, results[0].description)
}

fun testIndexResourceWithMhtml(authorityId: Id, searchIndex: SearchIndex) {
    val rootPath = Path(System.getProperty("user.dir"), "..", "sample-docs").toString()

    val path = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
    val message = path.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path")
    val mhtmlPage = MhtmlPage(message)

    val textExtractor = TextExtractor()
    val text = mhtmlPage.htmlText.nullOrElse { textExtractor.getBody(it.toByteArray()) }
    val resourceEntity = ResourceEntity(authorityId, mhtmlPage.uri, mhtmlPage.title, text = text)

    searchIndex.index(resourceEntity)
    val results = searchIndex.search("game of life")
    assertEquals(1, results.size)
    assertEquals(resourceEntity.description, results[0].description)
}