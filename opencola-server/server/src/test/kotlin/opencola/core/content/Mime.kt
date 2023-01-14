package opencola.core.content

import io.opencola.content.parseMime
import org.apache.james.mime4j.dom.Message
import java.nio.file.Path
import kotlin.io.path.inputStream

fun readMimeMessage(path: Path): Message {
    return path.inputStream().use { parseMime(it) ?: throw RuntimeException("Unable to parse $it") }
}