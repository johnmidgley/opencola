package io.opencola.content

import java.net.URI

// TODO - Implement ContentParserRegistry
interface ContentParser {
    fun parseTitle(): String?
    fun parseDescription(): String?
    fun parseImageUri(): URI?
    fun parseText(): String?
}