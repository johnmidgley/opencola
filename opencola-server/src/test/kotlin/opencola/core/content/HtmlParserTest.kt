package opencola.core.content

import org.junit.Test
import kotlin.io.path.Path
import kotlin.test.assertEquals

class HtmlParserTest {
    @Test
    fun testParseOgDescription(){
        val rootPath = Path(System.getProperty("user.dir"),"..", "sample-docs").toString()
        val mhtmlPage = readMhtmlPage(Path(rootPath, "body-language.mht"))
        val description = HtmlParser(mhtmlPage.htmlText!!).parseDescription()

        assertEquals(description,"NEW YORK—Explaining why she was in such high demand as an expert witness in courtrooms and as an analyst on news programs, body language specialist Linda Rothbaum told reporters Wednesday that she could discern with almost 90% accuracy whether a person was sitting. “While there’s always room for error, my training in…")
    }

}