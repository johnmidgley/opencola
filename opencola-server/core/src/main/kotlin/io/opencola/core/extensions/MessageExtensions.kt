package opencola.core.extensions

import org.apache.james.mime4j.dom.Message
import org.apache.james.mime4j.message.DefaultMessageWriter
import java.io.ByteArrayOutputStream

fun Message.bytes(): ByteArray {
    return  ByteArrayOutputStream().use {
        DefaultMessageWriter().writeMessage(this, it)
        it.toByteArray()
    }
}