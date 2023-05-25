package io.opencola.relay.common.message

import com.google.protobuf.ByteString
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.Signature
import io.opencola.security.isValidSignature
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyPair
import java.util.*

class Message(val header: Header, val body: ByteArray) {
    constructor(senderKeyPair: KeyPair, body: ByteArray)
            : this (Header(UUID.randomUUID(), senderKeyPair.public, Signature.of(senderKeyPair.private, body)), body)

    override fun toString(): String {
        return "Message(header=$header, body=${body.size} bytes)"
    }

    fun encode(): ByteArray {
        return encode(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    fun validate(): Message {
        if(!isValidSignature(header.from, body, header.signature.bytes)){
            throw RuntimeException("Invalid Signature")
        }

        return this
    }

    companion object : StreamSerializer<Message>, ProtoSerializable<Message, Proto.RelayMessage> {
        override fun encode(stream: OutputStream, value: Message) {
            Header.encode(stream, value.header)
            stream.writeByteArray(value.body)
        }

        override fun decode(stream: InputStream): Message {
            return Message(
                Header.decode(stream),
                stream.readByteArray())
        }

        override fun toProto(value: Message): Proto.RelayMessage {
            return Proto.RelayMessage.newBuilder()
                .setHeader(Header.toProto(value.header))
                .setBody(ByteString.copyFrom(value.body))
                .build()
        }

        override fun fromProto(value: Proto.RelayMessage): Message {
            return Message(
                Header.fromProto(value.header),
                value.body.toByteArray()
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.RelayMessage {
            return Proto.RelayMessage.parseFrom(bytes)
        }
    }
}