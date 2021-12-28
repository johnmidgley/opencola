package opencola.core.content

import opencola.core.extensions.nullOrElse
import opencola.core.model.Id
import org.apache.james.mime4j.dom.*
import org.apache.james.mime4j.field.ContentLocationFieldLenientImpl
import org.apache.james.mime4j.message.*
import org.apache.james.mime4j.stream.Field
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI


// TODO: Mhtml -> Mht
class MhtmlPage {
    val message: Message
    val uri: URI
    val title: String?

    // TODO: Make work on stream?
    constructor(message: Message) {
        // TODO: This is likely specific to Chrome saving. Should probably detect creator and dispatch to correct canonicalizer
        this.message = canonicalizeMessage(message)
        uri = message.header.getField("Snapshot-Content-Location")?.body.nullOrElse { URI(it) } ?: throw RuntimeException(
                "No URI specified in MHTML message"
            )
        title = message.header.getField("Subject")?.body
    }

    fun getDataId() : Id {
        val stream = ByteArrayOutputStream()
        stream.use{ DefaultMessageWriter().writeMessage(message, it) }
        return Id(stream.toByteArray())
    }

    private fun canonicalizeMessage(message: Message): Message {
        return Message.Builder.of()
            .addField(canonicalizeContentType(message.header.getField("Content-Type")))
            .setBody(canonicalizeBody(message.body)).build()
    }

    private val boundaryRegex = "boundary=\".*\"".toRegex()
    private val opencolaBoudary = "boundary=\"----MultipartBoundary--opencola--1516051403151201--562D739589761-----\""

    private fun canonicalizeContentType(field: Field): Field {
        // TODO: Test only assert that field.name = "Content-Type"?
        // Not super elegant, but no obvious way to construct the field (ContentTypeField is an interface and ContentTypeFieldImpl is private)
        val raw = String(field.raw.toByteArray())
        val message = raw.replace(boundaryRegex, opencolaBoudary).byteInputStream().use { parseMime(it) }

        if (message == null) {
            // TODO: Log warning / error
            println("Couldn't canonicalize Content-Type {$raw}. Using original")
            return field
        }

        return message.header.getField(field.name)
    }

    private fun canonicalizeBody(body: Body): Body {
        return when (body) {
            is Multipart -> canonicalizeMultipart(body)
            is TextBody -> canonicalizeStringBody(body)
            is BinaryBody -> canonicalizeBinaryBody(body)
            else -> {
                // To be robust, just copy through anything that is unexpected
                // TODO: Log warning
                body
            }
        }
    }

    private fun canonicalizeHeaderField(field: Field): Field? {
        return when (field.name) {
            "Content-ID" -> null
            "Content-Location" -> {
                // TODO: Investigate
                // This is odd. Not sure how stripping cids still works, especially when it doesn't work for http locations.
                // Likely styles are just loaded, so name doesn't matter. Probably cleaner to replace cid GUIDs with
                // deterministic ids
                if ((field as ContentLocationFieldLenientImpl).location.startsWith("cid")) null else field
            }
            else -> field
        }
    }

    private fun canonicalizeMultipart(multipart: Multipart): Multipart {
        val builder = MultipartBuilder.create()
        multipart.bodyParts.forEach {
            builder.addBodyPart(canonicalizeEntity(it))
        }
        return builder.build()
    }

    private fun canonicalizeEntity(entity: Entity): Entity {
        val builder = BodyPartBuilder()
        entity.header.fields.mapNotNull { canonicalizeHeaderField(it) }.forEach { builder.addField(it) }
        builder.body = canonicalizeBody(entity.body)
        return builder.build()
    }

    // TODO: This is overly specific. Match anything cid:css to "
    private val cidRegex = "cid:css-[0-9a-z]{8}(-[0-9a-z]{4}){3}-[0-9a-z]{12}@mhtml.blink".toRegex()

    private fun canonicalizeStringBody(body: TextBody): TextBody {
        return BasicBodyFactory.INSTANCE.textBody(body.reader.use { it.readText() }.replace(cidRegex, ""))
    }

    private fun canonicalizeBinaryBody(body: BinaryBody): BinaryBody {
        return body.inputStream.use { BasicBodyFactory.INSTANCE.binaryBody(it) }
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