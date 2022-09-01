package io.opencola.relay.common

import io.opencola.core.serialization.StreamSerializer
import io.opencola.core.serialization.readByteArray
import io.opencola.core.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream

class Message(val header: Header, val body: ByteArray) {
    companion object : StreamSerializer<Message> {
        override fun encode(stream: OutputStream, value: Message) {
            stream.writeByteArray(Header.encode(value.header))
            stream.writeByteArray(value.body)
        }

        override fun decode(stream: InputStream): Message {
            return Message(Header.decode(stream), stream.readByteArray())
        }
    }
}