package opencola.core.content

import io.opencola.content.getFirstImageFromPDF
import io.opencola.content.toBytes
import io.opencola.model.Id
import org.junit.Test
import kotlin.io.path.Path
import kotlin.io.path.readBytes
import kotlin.test.assertEquals

class PdfParserTest {

    @Test
    fun testParseFirstImage() {
        val rootPath = Path(System.getProperty("user.dir"), "../..", "sample-docs")
        val pdfPath = rootPath.resolve("Image_Super-Resolution_via_Iterative_Refinement.pdf")
        val pdfBytes = pdfPath.readBytes()

        val imageId = Id.ofData(getFirstImageFromPDF(pdfBytes)!!.toBytes("PNG"))
        assertEquals(imageId.toString(), "8V6Pkhf7rQ1cNeHH2d8kGyCg3bTMbzZXv2wkmPqxKziq")
    }
}