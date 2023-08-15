package io.opencola.relay.common.message

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.security.PublicKeyProtoCodec
import io.opencola.relay.common.protobuf.Relay as Proto
import io.opencola.security.DEFAULT_SIGNATURE_ALGO
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

class MessageHeader(val messageId: UUID, val from: PublicKey, val signature: Signature) {
    override fun toString(): String {
        return "Header(messageId=$messageId, from=${Id.ofPublicKey(from)})"
    }

    companion object : StreamSerializer<MessageHeader>, ProtoSerializable<MessageHeader, Proto.Header> {
        override fun encode(stream: OutputStream, value: MessageHeader) {
            // This was the original order of parameters, so need to keep it this way for backwards compatibility
            stream.writeByteArray(value.from.encoded)
            stream.writeByteArray(value.messageId.toByteArray())
            stream.writeByteArray(value.signature.bytes)
        }

        override fun decode(stream: InputStream): MessageHeader {
            // This was the original order of parameters, so need to keep it this way for backwards compatibility
            val from = publicKeyFromBytes(stream.readByteArray())
            val messageId = UUIDByteArrayCodecCodec.decode(stream.readByteArray())
            val signature = Signature(DEFAULT_SIGNATURE_ALGO, stream.readByteArray())

            return MessageHeader(messageId, from, signature)
        }

        override fun toProto(value: MessageHeader): Proto.Header {
            return Proto.Header.newBuilder()
                .setFrom(PublicKeyProtoCodec.toProto(value.from))
                .setMessageId(ByteString.copyFrom(value.messageId.toByteArray()))
                .setSignature(value.signature.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Header): MessageHeader {
            return MessageHeader(
                UUIDByteArrayCodecCodec.decode(value.messageId.toByteArray()),
                PublicKeyProtoCodec.fromProto(value.from),
                Signature.fromProto(value.signature)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Header {
            return Proto.Header.parseFrom(bytes)
        }
    }
}