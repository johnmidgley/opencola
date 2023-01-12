package io.opencola.relay.common


import io.opencola.core.security.encrypt
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

class MessageEnvelope(val to: PublicKey, val message: ByteArray) {
    constructor(to: PublicKey, message: Message) : this(to, encrypt(to, Message.encode(message)))

    companion object : StreamSerializer<MessageEnvelope> {
        override fun encode(stream: OutputStream, value: MessageEnvelope) {
            stream.writeByteArray(value.to.encoded)
            stream.writeByteArray(value.message)
        }

        override fun decode(stream: InputStream): MessageEnvelope {
            return MessageEnvelope(
                publicKeyFromBytes(stream.readByteArray()),
                stream.readByteArray())
        }
    }
}