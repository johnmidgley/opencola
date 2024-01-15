package io.opencola.content

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.sax.BodyContentHandler
import java.io.ByteArrayInputStream
import java.io.InputStream


// https://hub.docker.com/r/apache/tika
// https://cwiki.apache.org/confluence/display/TIKA/TikaServer#TikaServer-Buildingfromsource
// Think about using solr to do Tika analysis / parsing as it should be able to do this

// TODO: Make interface and TikaContentExtractor implementation
// TODO: Try Boilerpipe: https://github.com/kohlschutter/boilerpipe
class TextExtractor {
    private fun getBody(stream: InputStream) : String {
        val handler = BodyContentHandler(-1) // -1 removes default text limit
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
}

