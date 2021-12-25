import opencola.core.content.TextExtractor
import java.io.FileInputStream
import kotlin.io.path.Path

fun testCopntentAnalyzer() {
    val contentAnalyzer = TextExtractor()
    val path = System.getProperty("user.dir") + "/sample-docs/" + "Image_Super-Resolution_via_Iterative_Refinement.pdf"
    val fileType = FileInputStream(path).use { contentAnalyzer.getType(it) }
    println(fileType)
}

fun testGetBody(){
    val contentAnalyzer = TextExtractor()
    val path = Path(System.getProperty("user.dir") + "/sample-docs/" + "Image_Super-Resolution_via_Iterative_Refinement.pdf")
    val out = contentAnalyzer.getBody(path)
}
