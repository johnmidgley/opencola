package io.opencola.core.content

import io.ktor.utils.io.charsets.Charset
import io.opencola.core.extensions.nullOrElse
import io.opencola.core.model.Id
import org.apache.james.mime4j.codec.DecoderUtil
import org.apache.james.mime4j.dom.*
import org.apache.james.mime4j.message.*
import org.apache.james.mime4j.stream.Field
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI

fun Message.toBytes() : ByteArray {
    return ByteArrayOutputStream().use { outputStream ->
        DefaultMessageWriter().writeMessage(this, outputStream)
        outputStream.toByteArray()
    }
}

class MhtmlPage {
    val message: Message
    val uri: URI
    val title: String?
    val description: String?
    val imageUri: URI?
    val text: String?
    val htmlText : String?
    val embeddedMimeType : String?

    private val contentLocationMap: Map<String,String>

    // TODO: Make work on stream?
    constructor(message: Message) {
        // TODO: This is likely specific to Chrome saving. Should probably detect creator and dispatch to correct handler to canonicalize
        contentLocationMap = getContentLocationMap(message)
        this.message = canonicalizeMessage(message)
        uri = message.header.getField("Snapshot-Content-Location")?.body.nullOrElse { URI(it) }
            ?: throw RuntimeException("No URI specified in MHTML message")
        htmlText = parseHtmlText()

        val htmlParser = htmlText.nullOrElse { HtmlParser(it) }
        title = htmlParser?.parseTitle() ?: DecoderUtil.decodeEncodedWords(message.header.getField("Subject")?.body, Charset.defaultCharset())
        description = htmlParser.nullOrElse { it.parseDescription() }
        imageUri = htmlParser.nullOrElse { it.parseImageUri() } ?: getImageUri(message)
        embeddedMimeType = htmlParser?.let { it.parseEmbeddedType() }
        text = htmlText.nullOrElse { TextExtractor().getBody(it.toByteArray()) }
    }

    private fun parseHtmlText() : String? {
        // TODO: Clean up!
        val multipart = message.body as Multipart
        val bodyPart = multipart.bodyParts.firstOrNull { it.header.getField("Content-Type").body == "text/html" } ?: return null
        return (bodyPart.body as TextBody).reader.readText()
    }

    // TODO: Should this be private and/or called on construction? Only used in tests.
    fun getDataId() : Id {
        // TODO - is there a better way to do this?
        ByteArrayOutputStream().use{
            DefaultMessageWriter().writeMessage(message, it)
            return Id.ofData(it.toByteArray())
        }
    }

    private fun getContentLocationMap(message: Message): Map<String, String> {
        if(message.body !is Multipart){
            // Log warning
            return mapOf()
        }

        return (message.body as Multipart)
            .bodyParts.asSequence()
            .mapNotNull { it.header.getField("Content-Location") }
            .map{ it.body }
            .filter { it.startsWith("cid:css") }
            .distinct()
            .mapIndexed { i, s -> Pair(s, "cid:css-${i.toString().padStart(5,'0')}@mhtml.opencola") }
            .toMap()
    }

    private fun canonicalizeMessage(message: Message): Message {
        return Message.Builder.of()
            .addField(transformField(message.header.getField("Content-Type")){ it.replace(boundaryRegex, opencolaBoundary) })
            .addField(message.header.getField("Snapshot-Content-Location"))
            .setBody(canonicalizeBody(message.body)).build()
    }

    private val boundaryRegex = "boundary=\".*\"".toRegex()
    private val opencolaBoundary = "boundary=\"----MultipartBoundary--opencola--1516051403151201--562D739589761-----\""

    private fun transformField(field: Field, transform: (raw: String) -> String) : Field {
        // Not super elegant, but no obvious way to construct the field (ContentTypeField is an interface and ContentTypeFieldImpl is private)
        val raw = String(field.raw.toByteArray())
        val message = transform(raw).byteInputStream().use { parseMime(it) }

        if (message == null) {
            // TODO: Log warning / error
            println("Couldn't transform field {$raw}. Using original")
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
            "Content-Location" -> transformField(field) { it.replace(field.body, contentLocationMap.getOrDefault(field.body, field.body)) }
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
        val utf8Body = String(body.inputStream.readAllBytes(), Charsets.UTF_8)
        return BasicBodyFactory.INSTANCE.textBody(contentLocationMap.entries.fold(utf8Body) { text, (k, v) -> text.replace(k, v) }, Charsets.UTF_8)
    }

    private fun canonicalizeBinaryBody(body: BinaryBody): BinaryBody {
        // Copy here so that nobody can mutate unexpectedly
        return body.inputStream.use { BasicBodyFactory.INSTANCE.binaryBody(it) }
    }
}

fun parseMhtml(inputStream: InputStream): MhtmlPage? {
    val message = parseMime(inputStream)
    return if (message != null) MhtmlPage(message) else null
}