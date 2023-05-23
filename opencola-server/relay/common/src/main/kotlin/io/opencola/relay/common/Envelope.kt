package io.opencola.relay.common

import com.google.protobuf.ByteString
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.encrypt
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey

class Envelope(val to: PublicKey, val key: ByteArray?, val message: ByteArray) {
    constructor(to: PublicKey, key: ByteArray?, message: Message) : this(
        to,
        key,
        encrypt(to, Message.encode(message)).bytes
    )

    companion object : StreamSerializer<Envelope>,
        ProtoSerializable<Envelope, Proto.Envelope> {

        // V1 encoding does not include the key
        override fun encode(stream: OutputStream, value: Envelope) {
            stream.writeByteArray(value.to.encoded)
            stream.writeByteArray(value.message)
        }

        // V1 encoding does not include the key
        override fun decode(stream: InputStream): Envelope {
            return Envelope(
                publicKeyFromBytes(stream.readByteArray()),
                null,
                stream.readByteArray()
            )
        }

        override fun toProto(value: Envelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setTo(ByteString.copyFrom(value.to.encoded))
                .also {if (value.key != null) it.setKey(ByteString.copyFrom(value.key)) }
                .setMessage(ByteString.copyFrom(value.message))
                .build()
        }

        override fun fromProto(value: Proto.Envelope): Envelope {
            return Envelope(
                publicKeyFromBytes(value.to.toByteArray()),
                if (value.key.isEmpty) null else value.key.toByteArray(),
                value.message.toByteArray()
            )
        }
    }
}