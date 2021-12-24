package opencola.core.content

import opencola.core.extensions.nullOrElse
import org.apache.james.mime4j.dom.Message
import java.io.InputStream
import java.net.URI


class MhtmlPage {
    val message: Message
    val uri: URI
    val title: String?

    constructor(message: Message){
        // Strip unneeded headers (location not relevant and messes up content hash)
        // TODO: Strip location from body parts - messes up data id (i.e. same content from different places looks different)
        this.message = Message.Builder.of()
            .addField(message.header.getField("Content-Type"))
            .setBody(message.body).build()

        val header = message.header
        uri = header.getField("Snapshot-Content-Location")?.body.nullOrElse { URI(it) } ?: throw RuntimeException("No URI specified in MHTML message")
        title = header.getField("Subject")?.body
    }

    private fun getHeaderField(message: Message, name: String) : String? {
        return message.header.getField(name)?.body
    }
}


// TODO: Store indexed pages as mht archives.
//  Analysis can be done with https://github.com/apache/james-mime4j
//  https://github.com/apache/james-mime4j/blob/master/examples/src/main/java/org/apache/james/mime4j/samples/dom/ParsingMessage.java
//  OR
//  Java Mail APIs
//  https://pretagteam.com/question/how-to-read-or-parse-mhtml-mht-files-in-java
//  which uses https://javaee.github.io/javamail/
fun parseMhtml(inputStream: InputStream): MhtmlPage? {
    val message = parseMime(inputStream)
    return if (message != null) MhtmlPage(message) else null
}