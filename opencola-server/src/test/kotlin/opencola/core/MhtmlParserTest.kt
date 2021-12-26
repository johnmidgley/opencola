package opencola.core

import opencola.core.content.MhtmlPage
import opencola.core.content.parseMime
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class MhtmlParserTest {
    @Test
    fun testContentEquals(){
        val rootPath = Path(System.getProperty("user.dir"),"sample-docs").toString()
        val path1 = Path(rootPath, "Conway's Game of Life - Wikipedia.mht")
        val path2 = Path(rootPath, "Conway's Game of Life - Wikipedia 2.mht")

        val message1 = path1.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path1")
        val message2 = path2.inputStream().use { parseMime(it) } ?: throw RuntimeException("Unable to parse $path2")

        val mhtmlPage1 = MhtmlPage(message1)
        val mhtmlPage2 = MhtmlPage(message2)

        assertTrue("Pages with same content do not pass contentEquals." ) { mhtmlPage1.contentEquals(mhtmlPage2) }
    }
}