package io.opencola.content

import java.net.URI

abstract class AbstractContentParser(val content: ByteArray, val uri: URI? = null) : ContentParser {
    override fun parseTitle(): String? {
        return uri?.let { uri.toString() }
    }
}