package opencola.core

import opencola.core.content.MhtmlPage
import opencola.core.content.parseMime
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MhtmlParserTest {
    private fun readMhtmlPage(path: Path): MhtmlPage {
        return MhtmlPage(path.inputStream().use { parseMime(it) ?: throw RuntimeException("Unable to parse $it") })
    }

    @Test
    fun testContentEquals(){
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()
        val mhtmlPage1 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))
        val mhtmlPage2 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia 2.mht"))
        assertEquals(mhtmlPage1.getDataId(), mhtmlPage2.getDataId())
    }
}