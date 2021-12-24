package opencola.core.content

import opencola.core.extensions.nullOrElse
import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import java.io.InputStream
import java.net.URI


class Mhtml(val message: Message) {
    private fun getHeaderField(name: String) : String? {
        return message.header.getField(name)?.body
    }

    fun getUri(): URI {
        return getHeaderField("Snapshot-Content-Location").nullOrElse { URI(it) } ?: throw RuntimeException("No URI specified in MHTML message")
    }

    fun getTitle() : String? {
        return getHeaderField("Subject")
    }

}


// TODO: Store indexed pages as mht archives.
//  Analysis can be done with https://github.com/apache/james-mime4j
//  https://github.com/apache/james-mime4j/blob/master/examples/src/main/java/org/apache/james/mime4j/samples/dom/ParsingMessage.java
//  OR
//  Java Mail APIs
//  https://pretagteam.com/question/how-to-read-or-parse-mhtml-mht-files-in-java
//  which uses https://javaee.github.io/javamail/
fun parseMhtml(inputStream: InputStream): Mhtml? {
    val message = parseMime(inputStream)
    return if (message != null) Mhtml(message) else null
}