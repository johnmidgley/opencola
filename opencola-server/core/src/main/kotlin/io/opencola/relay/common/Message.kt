package io.opencola.relay.common

import io.opencola.core.security.Signature
import io.opencola.core.security.isValidSignature
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.util.*

class Message(val header: Header, val body: ByteArray) {
    constructor(senderKeyPair: KeyPair, sessionId: UUID, body: ByteArray)
            : this (Header(senderKeyPair.public, sessionId, Signature.of(senderKeyPair.private, body)), body)

    override fun toString(): String {
        return "Message(header=$header, body=${body.size} bytes)"
    }

    fun validate(): Message {
        if(!isValidSignature(header.from, body, header.signature.bytes)){
            throw RuntimeException("Invalid Signature")
        }

        return this
    }

    companion object : StreamSerializer<Message> {
        override fun encode(stream: OutputStream, value: Message) {
            Header.encode(stream, value.header)
            stream.writeByteArray(value.body)
        }

        override fun decode(stream: InputStream): Message {
            return Message(
                Header.decode(stream),
                stream.readByteArray())
        }
    }
}