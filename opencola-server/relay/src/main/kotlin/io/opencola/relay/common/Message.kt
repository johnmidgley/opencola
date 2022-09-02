package io.opencola.relay.common

import io.opencola.core.security.publicKeyFromBytes
import io.opencola.core.serialization.StreamSerializer
import io.opencola.core.serialization.UUIDByteArrayCodecCodec
import io.opencola.core.serialization.readByteArray
import io.opencola.core.serialization.writeByteArray
import opencola.core.extensions.toByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.*

class Message(val from: PublicKey, val sessionId: UUID, val body: ByteArray) {
    companion object : StreamSerializer<Message> {
        override fun encode(stream: OutputStream, value: Message) {
            stream.writeByteArray(value.from.encoded)
            stream.writeByteArray(value.sessionId.toByteArray())
            stream.writeByteArray(value.body)
        }

        override fun decode(stream: InputStream): Message {
            return Message(
                publicKeyFromBytes(stream.readByteArray()),
                UUIDByteArrayCodecCodec.decode(stream.readByteArray()),
                stream.readByteArray())
        }
    }
}