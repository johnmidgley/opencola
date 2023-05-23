package io.opencola.relay.common

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.SIGNATURE_ALGO
import io.opencola.security.Signature
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.codecs.UUIDByteArrayCodecCodec
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import io.opencola.util.toByteArray
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.*

class Header(val messageId: UUID, val from: PublicKey, val signature: Signature) {
    override fun toString(): String {
        return "Header(messageId=$messageId, from=${Id.ofPublicKey(from)})"
    }

    companion object : StreamSerializer<Header>, ProtoSerializable<Header, Proto.Header> {
        override fun encode(stream: OutputStream, value: Header) {
            // This was the original order of parameters, so need to keep it this way for backwards compatibility
            stream.writeByteArray(value.from.encoded)
            stream.writeByteArray(value.messageId.toByteArray())
            stream.writeByteArray(value.signature.bytes)
        }

        override fun decode(stream: InputStream): Header {
            // This was the original order of parameters, so need to keep it this way for backwards compatibility
            val from = publicKeyFromBytes(stream.readByteArray())
            val messageId = UUIDByteArrayCodecCodec.decode(stream.readByteArray())
            val signature = Signature(SIGNATURE_ALGO, stream.readByteArray())

            return Header(messageId, from, signature)
        }

        override fun toProto(value: Header): Proto.Header {
            return Proto.Header.newBuilder()
                .setFrom(ByteString.copyFrom(value.from.encoded))
                .setMessageId(ByteString.copyFrom(value.messageId.toByteArray()))
                .setSignature(value.signature.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Header): Header {
            return Header(
                UUIDByteArrayCodecCodec.decode(value.messageId.toByteArray()),
                publicKeyFromBytes(value.from.toByteArray()),
                Signature.fromProto(value.signature)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Header {
            return Proto.Header.parseFrom(bytes)
        }
    }
}