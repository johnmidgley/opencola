package opencola.core.content

import org.junit.Test
import kotlin.io.path.*
import kotlin.test.assertEquals

class SplitMhtTest {
    @Test
    fun testSplitMht(){
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()
        val mimeMessage = readMimeMessage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))
        val directory = createTempDirectory()
        val parts = splitMht(mimeMessage)

        parts.forEach{
            val (filename, content) = it
            Path(directory.pathString, filename).outputStream().use{ os ->
                os.write(content)
            }
        }

        assert(parts.isNotEmpty())
        assertEquals(parts[0].first, "Conway's Game of Life - Wikipedia.html" )
    }
}