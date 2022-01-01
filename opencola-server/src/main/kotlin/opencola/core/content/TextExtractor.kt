package opencola.core.content

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Path


// https://hub.docker.com/r/apache/tika
// https://cwiki.apache.org/confluence/display/TIKA/TikaServer#TikaServer-Buildingfromsource
// Think about using solr to do Tika analysis / parsing as it should be able to do this

class TextExtractor {
    private val tika = Tika()

    fun getType(inStream: InputStream): String {
        // TODO: Is application/octet-stream the best choice for arbitrary binary data
        return tika.detect(inStream) ?: "application/octet-stream"
    }

    fun getType(bytes: ByteArray) : String {
        ByteArrayInputStream(bytes).use { return getType(it)}
    }

    fun getBody(stream: InputStream) : String {
        val handler = BodyContentHandler()
        val metadata = Metadata()
        val parser = AutoDetectParser()
        val context = ParseContext()

        parser.parse(stream, handler, metadata, context)

        // TODO: Metadata may not include title. Fall back to normalized name from path
        return handler.toString()
    }

    fun getBody(bytes: ByteArray) : String {
        ByteArrayInputStream(bytes).use { return getBody(it) }
    }

    // TODO: Remove or make call other getBody based on stream
    fun getBody(path: Path): String {
        TikaInputStream.get(path).use { stream ->
            val handler = BodyContentHandler()
            val metadata = Metadata()
            val parser = AutoDetectParser()
            val context = ParseContext()

            parser.parse(stream, handler, metadata, context)

            // TODO: Metadata may not include title. Fall back to normalized name from path
            return handler.toString()
        }
    }
}

