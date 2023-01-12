package opencola.core.content

import io.opencola.core.content.MhtmlPage
import io.opencola.core.content.parseMime
import io.opencola.core.model.Id
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals

fun getMimeSnapshotForUrl(url: String) : String {
    return """From: <Saved by Blink>
Snapshot-Content-Location: $url
Subject: 
Date: Wed, 26 Oct 2022 21:45:50 -0000
MIME-Version: 1.0
Content-Type: multipart/related;
	type="text/html";
	boundary="----MultipartBoundary--td2WB82m6rxEtr7uRJwL0aFpcCBN4ZOcVHjEMwc09z----"


------MultipartBoundary--td2WB82m6rxEtr7uRJwL0aFpcCBN4ZOcVHjEMwc09z----
Content-Type: text/html
Content-ID: <frame-C318472666F631B24C9F3095357D8BD0@mhtml.blink>
Content-Transfer-Encoding: quoted-printable
Content-Location: $url

<!DOCTYPE html><html><head><meta http-equiv=3D"Content-Type" content=3D"tex=
t/html; charset=3Dwindows-1252"></head><body style=3D"height: 100%; width: =
100%; overflow: hidden; margin:0px; background-color: rgb(82, 86, 89);"><em=
bed name=3D"0344039E3E68F745D3B632020CD0CBE7" style=3D"position:absolute; l=
eft: 0; top: 0;" width=3D"100%" height=3D"100%" src=3D"about:blank" type=3D=
"application/pdf" internalid=3D"0344039E3E68F745D3B632020CD0CBE7"></body></=
html>
------MultipartBoundary--td2WB82m6rxEtr7uRJwL0aFpcCBN4ZOcVHjEMwc09z------
"""
}

fun readMhtmlPage(path: Path): MhtmlPage {
    return MhtmlPage(path.inputStream().use { parseMime(it) ?: throw RuntimeException("Unable to parse $it") })
}

class MhtmlParserTest {
    private fun writeMhtmlPage(path: Path, page: MhtmlPage){
        path.outputStream().use{
            DefaultMessageWriter().writeMessage(page.message, it)
        }
    }

    @Test
    fun testContentEquals(){
        // Test that 2 versions of the same page saved at different times are equal after canonicalization
        val rootPath = Path(System.getProperty("user.dir"),"../..", "sample-docs").toString()
        val mhtmlPage1 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))
        val mhtmlPage2 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia 2.mht"))

        assertEquals(mhtmlPage1.getDataId(), mhtmlPage2.getDataId())
    }

    @Test
    fun testCanonicalMhtmlStability(){
        // Test that an original mht file matches a known canonicalization. This provides some protection against
        // unintentional changes to canonicalization
        val rootPath = Path(System.getProperty("user.dir"),"../..", "sample-docs").toString()
        val mhtmlPage1 = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))
        val stableMhtmlBytes = Path(rootPath, "Conway's Game of Life - Wikipedia.opencola.mht").readBytes()

        assertEquals(mhtmlPage1.getDataId(), Id.ofData(stableMhtmlBytes))
    }

    @Test
    fun testGetImageUri(){
        val rootPath = Path(System.getProperty("user.dir"),"../..", "sample-docs").toString()
        val mhtmlPage = readMhtmlPage(Path(rootPath, "Conway's Game of Life - Wikipedia.mht"))


        assertEquals(URI("https://upload.wikimedia.org/wikipedia/commons/e/e5/Gospers_glider_gun.gif"),
            mhtmlPage.imageUri)
    }
}