package opencola.core.content

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import java.io.FileInputStream
import java.nio.file.Path


// https://hub.docker.com/r/apache/tika
// https://cwiki.apache.org/confluence/display/TIKA/TikaServer#TikaServer-Buildingfromsource
// Think about using solr to do Tika analysis / parsing as it should be able to do this

class TextExtractor {
    private val tika = Tika()

    fun getType(inStream: FileInputStream): String? {
        return tika.detect(inStream)
    }

    fun getBody(path: Path): String? {
        // TODO: Does this stream need to be closed?
        val stream = TikaInputStream.get(path)
        val handler = BodyContentHandler()
        val metadata = Metadata()
        val parser = AutoDetectParser()
        val context = ParseContext()

        parser.parse(stream, handler, metadata, context)

        // TODO: Metadata may not include title. Fall back to normalized name from path
        return handler.toString()
    }
}

