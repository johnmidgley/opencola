package opencola.core.content

import opencola.core.extensions.nullOrElse
import org.apache.james.mime4j.dom.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI


class MhtmlPage {
    val message: Message
    val uri: URI
    val title: String?

    // TODO: Make work on stream?
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

    fun contentEquals(other: MhtmlPage): Boolean {
        val body = this.message.body as Multipart
        val otherBody = other.message.body as Multipart
        if(body.bodyParts.size != otherBody.bodyParts.size) return false

        return body.bodyParts.zip(otherBody.bodyParts).all {
            it.first.contentEquals(it.second)
        }
    }
}

private val cidRegex = "cid:css-[0-9a-z]{8}(-[0-9a-z]{4}){3}-[0-9a-z]{12}@mhtml.blink".toRegex()

fun TextBody.contentEquals(other: Any?) : Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    val otherTextBody = other as TextBody
    if(mimeCharset != otherTextBody.mimeCharset) return false

    val thisContent = ByteArrayOutputStream().use {
        this.writeTo(it)
        it.toString()
    }

    val otherContent = ByteArrayOutputStream().use {
        otherTextBody.writeTo(it)
        it.toString()
    }

    // TODO: This is super Chrome dependent. Think about how to make this more robust
    val canonicalContent = cidRegex.replace(thisContent, "")
    val canonicalOtherContent = cidRegex.replace(otherContent, "")

    return canonicalContent == canonicalOtherContent
}

fun BinaryBody.contentEquals(other: Any?) : Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    val otherBinaryBody = other as BinaryBody

    val thisContent = ByteArrayOutputStream().use {
        this.writeTo(it)
        it.toByteArray()
    }

    val otherContent = ByteArrayOutputStream().use {
        otherBinaryBody.writeTo(it)
        it.toByteArray()
    }

    return thisContent.contentEquals(otherContent)
}


fun Entity.contentEquals(other: Any?) : Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    val otherBody = (other as Entity).body
    if(this.body.javaClass != otherBody.javaClass) return false

    when(body){
        is TextBody -> return (body as TextBody).contentEquals(otherBody)
        is BinaryBody -> return (body as BinaryBody).contentEquals(otherBody)
        else ->
            throw NotImplementedError("Entity.contentEquals can't handle $body.javaClass")
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