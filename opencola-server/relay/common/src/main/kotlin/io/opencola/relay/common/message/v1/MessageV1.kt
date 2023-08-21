package io.opencola.relay.common.message.v1

import io.opencola.security.Signature
import io.opencola.security.isValidSignature
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.util.*

class MessageV1(val header: MessageHeader, val body: ByteArray) {
    constructor(senderKeyPair: KeyPair, body: ByteArray)
            : this (MessageHeader(UUID.randomUUID(), senderKeyPair.public, Signature.of(senderKeyPair.private, body)), body)

    override fun toString(): String {
        return "Message(header=$header, body=${body.size} bytes)"
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    fun validate(): MessageV1 {
        if(!isValidSignature(header.from, body, header.signature.bytes)){
            throw RuntimeException("Invalid Signature")
        }

        return this
    }

    companion object : StreamSerializer<MessageV1> {
        override fun encode(stream: OutputStream, value: MessageV1) {
            MessageHeader.encode(stream, value.header)
            stream.writeByteArray(value.body)
        }

        override fun decode(stream: InputStream): MessageV1 {
            return MessageV1(
                MessageHeader.decode(stream),
                stream.readByteArray())
        }
    }
}