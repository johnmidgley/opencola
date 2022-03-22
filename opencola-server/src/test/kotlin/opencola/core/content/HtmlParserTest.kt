package opencola.core.content

import org.junit.Test
import java.net.URI
import kotlin.io.path.Path
import kotlin.test.assertEquals

class HtmlParserTest {
    @Test
    fun testParseDescription(){
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()
        val mhtmlPage = readMhtmlPage(Path(rootPath, "body-language.mht"))

        assertEquals("NEW YORK—Explaining why she was in such high demand as an expert witness in courtrooms and as an analyst on news programs, body language specialist Linda Rothbaum told reporters Wednesday that she could discern with almost 90% accuracy whether a person was sitting. “While there’s always room for error, my training in…",
            HtmlParser(mhtmlPage.htmlText!!).parseDescription())
    }

    @Test
    fun testParseImageUri() {
        val rootPath = Path(System.getProperty("user.dir"), "..", "sample-docs").toString()
        val mhtmlPage = readMhtmlPage(Path(rootPath, "body-language.mht"))

        assertEquals(URI("https://i.kinja-img.com/gawker-media/image/upload/c_fill,f_auto,fl_progressive,g_center,h_675,pg_1,q_80,w_1200/4319e530fb6ea0a32066eaac679e316e.jpg"),
            HtmlParser(mhtmlPage.htmlText!!).parseImageUri())
    }
}