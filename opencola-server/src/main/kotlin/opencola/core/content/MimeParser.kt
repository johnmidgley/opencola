package opencola.core.content

import org.apache.james.mime4j.dom.*
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import java.io.InputStream

fun parseMime(inputStream: InputStream): Message? {
    // TODO: Think about a .thread extension that allows for a chain of operations on an original value
    val defaultMessageBuilder = DefaultMessageBuilder()
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    return defaultMessageBuilder.parseMessage(inputStream)
}

fun transformTextContent(body: Body, textTransform: (String) -> String): ByteArray {
    return when (body) {
        is TextBody -> textTransform(String(body.inputStream.readAllBytes())).toByteArray()
        is BinaryBody -> body.inputStream.use { it.readBytes() }
        else -> throw RuntimeException("Unhandled body type")
    }
}

fun normalizeExtension(extension: String): String {
    // TODO: Read from config
    val extensionMap = mapOf("svg+xml" to "svg")
    return extensionMap[extension] ?: extension
}

data class Part(val name: String, val mimeType: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Part

        if (name != other.name) return false
        if (mimeType != other.mimeType) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

// TODO: Validate that the Mime message is Mht, as expected
fun splitMht(message: Message): List<Part> {
    val multipart = message.body as? Multipart ?: throw RuntimeException("Attempt to split a message that is not Multipart")

    val locationMap = multipart.bodyParts.mapIndexed { index, part ->
        val basename = "$index"
        // TODO: Necessary to normalize?
        val extension = normalizeExtension(part.header.contentType().subType)
        Pair(part.header.contentLocation().location, "$basename.$extension")
    }.toMap()

    val parts = multipart.bodyParts.map {
        val location = locationMap[it.header.contentLocation().location] as String
        val content = transformTextContent(it.body) { location ->
            // Drop first part (root html) so that we don't replace the root url anywhere
            locationMap.entries.drop(1).fold(location) { acc, entry ->
                acc.replace(entry.key, entry.value)
            }
        }

        Part(location, it.header.contentType().mimeType, content)
    }

    return parts
}

