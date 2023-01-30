package opencola.server.handlers


import opencola.core.TestApplication
import opencola.core.content.getMimeSnapshotForUrl
import org.junit.Test
import kotlin.io.path.Path


class ActionTest {
    val app = TestApplication.instance

    @Test
    fun testSavePDF() {
        val sampleDocsPath = Path(System.getProperty("user.dir"),"../..", "sample-docs")
        val pdfPath = sampleDocsPath.resolve("Image_Super-Resolution_via_Iterative_Refinement.pdf")
//        val pdfBytes = pdfPath.inputStream().use { it.readAllBytes() }
//        val textExtractor = TextExtractor()
//
//        val type = textExtractor.getType(pdfBytes)
//        val text = textExtractor.getBody(pdfBytes)
//
//        val tmpFile = TestApplication.getTmpFilePath(".png")
//        tmpFile.outputStream().use {  ImageIO.write(getFirstImageFromPDF(pdfBytes)!!, "PNG", it) }


        val mhtml = getMimeSnapshotForUrl("file://$pdfPath").toByteArray()
        handleAction(app.getPersonas().single().entityId, app.inject(), app.inject(), "save", "true", mhtml)

        println("Done")
    }

}