package opencola.core.content

import opencola.core.model.Id
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals

class MhtmlParserTest {
    private fun readMhtmlPage(path: Path): MhtmlPage {
        return MhtmlPage(path.inputStream().use { parseMime(it) ?: throw RuntimeException("Unable to parse $it") })
    }

    private fun writeMhtmlPage(path: Path, page: MhtmlPage){
        path.outputStream().use{
            DefaultMessageWriter().writeMessage(page.message, it)
        }
    }

    @Test
    fun testContentEquals(){
        // Test that 2 versions of the same page saved at different times are equal after canonicalization
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()
        val mhtmlPage1 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))
        val mhtmlPage2 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia 2.mht"))

        assertEquals(mhtmlPage1.getDataId(), mhtmlPage2.getDataId())
    }

    @Test
    fun testCanonicalMhtmlStability(){
        // Test that an original mht file matches a known canonicalization. This provides some protection against
        // unintentional changes to canonicalization
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()
        val mhtmlPage1 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))
        val stableMhtmlBytes = Path(rootPath, "Conway's Game of Life - Wikipedia.opencola.mht").readBytes()

        assertEquals(mhtmlPage1.getDataId(), Id.ofData(stableMhtmlBytes))
    }
}