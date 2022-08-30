package opencola.core.content

import io.opencola.core.content.splitMht
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
            Path(directory.pathString, it.name).outputStream().use{ os ->
                os.write(it.bytes)
            }
        }

        assert(parts.isNotEmpty())
        assertEquals("0.html", parts[0].name)
    }
}