package opencola.core

import opencola.core.content.MhtmlPage
import opencola.core.content.parseMime
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MhtmlParserTest {
    @Test
    fun testContentEquals(){
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()

        val path1 = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
        val message1 = path1.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path1")
        val mhtmlPage1 = MhtmlPage(message1)

        val path2 = Path(rootPath, "Conway's Game of Life - Wikipedia 2.mht")
        val message2 = path2.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path2")
        val mhtmlPage2 = MhtmlPage(message2)

        assertEquals(mhtmlPage1.getDataId(), mhtmlPage2.getDataId())
    }
}