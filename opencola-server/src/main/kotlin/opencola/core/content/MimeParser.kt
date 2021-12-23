package opencola.core.content

import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import java.io.InputStream

fun parseMime(inputStream: InputStream): Message? {
    val defaultMessageBuilder = DefaultMessageBuilder()
    defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
    return defaultMessageBuilder.parseMessage(inputStream)
}