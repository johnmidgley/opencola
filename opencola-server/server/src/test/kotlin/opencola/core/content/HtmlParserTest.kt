package opencola.core.content

import io.opencola.content.HtmlParser
import io.opencola.content.MhtmlPage
import io.opencola.content.parseMime
import org.junit.Test
import java.net.URI
import kotlin.io.path.Path
import kotlin.test.assertEquals

class HtmlParserTest {
    @Test
    fun testParseHeadTitle(){
        val testTitle = "Test Title"
        val parser = HtmlParser("<head><title>$testTitle</title></body")
        assertEquals(testTitle, parser.parseTitle())
    }

    @Test
    fun testParseOGTitle(){
        val testTitle = "Test Title"
        val parser = HtmlParser("<head><meta property=\"og:title\" content=\"$testTitle\"></head>")
        assertEquals(testTitle, parser.parseTitle())
    }

    @Test
    fun testParseTwitterTitle(){
        val testTitle = "Test Title"
        val parser = HtmlParser("<head><meta name=\"twitter:title\" content=\"$testTitle\"></head>")
        assertEquals(testTitle, parser.parseTitle())
    }

    @Test
    fun testParseDescription(){
        val rootPath = Path(System.getProperty("user.dir"),"../..", "sample-docs").toString()
        val mhtmlPage = readMhtmlPage(Path(rootPath, "body-language.mht"))

        assertEquals("NEW YORK—Explaining why she was in such high demand as an expert witness in courtrooms and as an analyst on news programs, body language specialist Linda Rothbaum told reporters Wednesday that she could discern with almost 90% accuracy whether a person was sitting. “While there’s always room for error, my training in…",
            HtmlParser(mhtmlPage.htmlText!!).parseDescription())
    }

    @Test
    fun testParseImageUri() {
        val rootPath = Path(System.getProperty("user.dir"), "../..", "sample-docs").toString()
        val mhtmlPage = readMhtmlPage(Path(rootPath, "body-language.mht"))

        assertEquals(URI("https://i.kinja-img.com/gawker-media/image/upload/c_fill,f_auto,fl_progressive,g_center,h_675,pg_1,q_80,w_1200/4319e530fb6ea0a32066eaac679e316e.jpg"),
            HtmlParser(mhtmlPage.htmlText!!).parseImageUri())
    }

    @Test
    fun testParseMetaDescription(){
        val testDescription = "Test Description"
        val parser = HtmlParser("<head><meta name=\"description\" content=\"$testDescription\"></body>")
        assertEquals(testDescription, parser.parseDescription())
    }

    @Test
    fun testParseOgDescription(){
        val testDescription = "Test Description"
        val parser = HtmlParser("<head><meta property=\"og:description\" content=\"$testDescription\"></body>")
        assertEquals(testDescription, parser.parseDescription())
    }

    @Test
    fun testParseTwitterDescription(){
        val testDescription = "Test Description"
        val parser = HtmlParser("<head><meta name=\"twitter:description\" content=\"$testDescription\"></body>")
        assertEquals(testDescription, parser.parseDescription())
    }

    @Test
    fun testParseFirstBodyParagraph(){
        val testDescription = "Test Description"
        val parser = HtmlParser("<body><p>$testDescription</p></body>")
        assertEquals(testDescription, parser.parseDescription())
    }

    @Test
    fun testParseMetaDescriptionNoContent(){
        val parser = HtmlParser("<head><meta name=\"description\"></body>")
        assertEquals(null, parser.parseDescription())
    }

    @Test
    fun testParseMetaImage(){
        val testImageUri = URI("http://opencola.io/img.png")
        val parser = HtmlParser("<head><meta name=\"image\" content=\"$testImageUri\"></body>")
        assertEquals(testImageUri, parser.parseImageUri())
    }

    @Test
    fun testParseOgImage(){
        val testImageUri = URI("http://opencola.io/img.png")
        val parser = HtmlParser("<head><meta property=\"og:image\" content=\"$testImageUri\"></body>")
        assertEquals(testImageUri, parser.parseImageUri())
    }

    @Test
    fun testParseTwitterImage(){
        val testImageUri = URI("http://opencola.io/img.png")
        val parser = HtmlParser("<head><meta name=\"twitter:image\" content=\"$testImageUri\"></body>")
        assertEquals(testImageUri, parser.parseImageUri())
    }

    @Test
    fun testGetEmbeddedTypePDF() {
        val mhtmlPage =  MhtmlPage(parseMime(getMimeSnapshotForUrl("http://test"))!!)
        val htmlParser = HtmlParser(mhtmlPage.htmlText!!)
        val embeddedType = htmlParser.parseEmbeddedType()

        assertEquals("application/pdf", embeddedType)
    }
}