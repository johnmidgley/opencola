package io.opencola.content

import org.apache.tika.Tika
import java.io.InputStream

class TikaContentTypeDetector : ContentTypeDetector{
    private val tika = Tika()

    override fun getType(inStream: InputStream): String {
        return tika.detect(inStream) ?: "application/octet-stream"
    }
}