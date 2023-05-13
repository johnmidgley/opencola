package io.opencola.content

import java.io.ByteArrayInputStream
import java.io.InputStream

interface ContentTypeDetector {
    fun getType(inStream: InputStream): String
    fun getType(bytes: ByteArray) : String {
        ByteArrayInputStream(bytes).use { return getType(it)}
    }
}