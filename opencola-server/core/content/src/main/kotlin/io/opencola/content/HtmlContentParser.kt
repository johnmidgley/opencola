package io.opencola.content

import java.net.URI

class HtmlContentParser(content: ByteArray, uri: URI?) : AbstractContentParser(content, uri) {
    val htmlParser = JsoupHtmlParser(String(content))

    override fun parseTitle(): String? {
        return htmlParser.parseTitle() ?: super.parseTitle()
    }

    override fun parseDescription(): String? {
        return htmlParser.parseDescription()
    }

    override fun parseImageUri(): URI? {
        return htmlParser.parseImageUri()
    }

    override fun parseText(): String? {
        return htmlParser.parseText()
    }

}