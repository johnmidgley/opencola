package opencola.core.content

import io.opencola.content.MhtmlPage
import io.opencola.content.parseMime
import org.apache.james.mime4j.dom.Message
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.io.path.Path
import kotlin.io.path.readBytes
import kotlin.test.assertEquals

class MhtEncodingTest {
    private fun loadSampleMime(name: String): Message {
        val docBytes = Path(System.getProperty("user.dir")).resolve("../../sample-docs").resolve(name).readBytes()
        return parseMime(ByteArrayInputStream(docBytes))!!
    }

    @Test
    fun testTitleEncoding(){
        val message = loadSampleMime("I’ve been waiting 15 years for Facebook to die. I’m more hopeful than ever _ Cory Doctorow _ The Guardian.mht")
        val mhtmlPage = MhtmlPage(message)

        assertEquals("I’ve been waiting 15 years for Facebook to die. I’m more hopeful than ever | Cory Doctorow | The Guardian", mhtmlPage.title)
    }
}