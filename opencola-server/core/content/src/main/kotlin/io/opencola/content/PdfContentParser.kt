package io.opencola.content

import java.net.URI

abstract class PdfContentParser(pdfBytes: ByteArray, uri: URI?) : AbstractContentParser(pdfBytes, uri) {
    override fun parseTitle(): String? {
        return null
    }

    override fun parseDescription(): String? {
        return null
    }

    override fun parseImageUri(): URI? {
        return null
    }

    override fun parseText(): String? {
        return null
    }
}