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
        is TextBody -> textTransform(body.reader.use { it.readText() }).toByteArray()
        is BinaryBody -> body.inputStream.use { it.readBytes() }
        else -> throw RuntimeException("Unhandled body type")
    }
}

fun normalizeExtension(extension: String): String {
    // TODO: Read from config
    val extensionMap = mapOf("svg+xml" to "svg")
    return extensionMap[extension] ?: extension
}

// TODO: Validate that the Mime message is Mht, as expected
fun splitMht(message: Message): List<Pair<String, ByteArray>> {
    val multipart = message.body as? Multipart ?: throw RuntimeException("Attempt to split a message that is not Multipart")

    // Subject is that name of the original html file, so use it rather than 0.html
    val subject = message.header.unstructuredField("Subject").value

    val locationMap = multipart.bodyParts.mapIndexed { index, part ->
        val basename = "${if (index == 0) subject else "$index"}"
        val extension = normalizeExtension("${part.header.contentType().subType}")
        Pair(part.header.contentLocation().location, "$basename.$extension")
    }.toMap()

    val parts = multipart.bodyParts.map {
        val location = locationMap[it.header.contentLocation().location] ?: throw RuntimeException("This shouldn't be possible :)")
        val content = transformTextContent(it.body) { location ->
            locationMap.entries.fold(location) { acc, entry ->
                acc.replace(entry.key, entry.value)
            }
        }
        Pair(location, content)
    }

    return parts
}

