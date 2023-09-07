package opencola.server.handlers


import io.opencola.application.TestApplication
import io.opencola.content.parseMhtml
import opencola.core.content.getMimeSnapshotForUrl
import org.junit.Test
import kotlin.io.path.Path


class ActionTest {
    val app = TestApplication.instance

    @Test
    fun testSavePDF() {
        val sampleDocsPath = Path(System.getProperty("user.dir"), "../..", "sample-docs")
        val pdfPath = sampleDocsPath.resolve("Image_Super-Resolution_via_Iterative_Refinement.pdf")
        val mhtmlPage = getMimeSnapshotForUrl("file://$pdfPath")
            .toByteArray()
            .inputStream()
            .use { parseMhtml(it) ?: throw RuntimeException("Unable to parse mhtml") }

        handleAction(app.getPersonas().single().entityId, app.inject(), app.inject(), "save", "true", mhtmlPage)
        println("Done")
    }

}