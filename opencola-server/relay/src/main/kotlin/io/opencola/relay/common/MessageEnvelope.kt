package io.opencola.relay.common


import io.opencola.core.security.encrypt
import io.opencola.core.security.publicKeyFromBytes
import io.opencola.core.serialization.StreamSerializer
import io.opencola.core.serialization.readByteArray
import io.opencola.core.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

// TODO: Should encrypt be done with session key? Make session id encrypted session key?
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