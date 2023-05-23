package io.opencola.relay.common

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.relay.common.protobuf.Relay
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

class Header(val from: PublicKey, val sessionId: UUID, val signature: Signature) {
    override fun toString(): String {
        return "Header(from=${Id.ofPublicKey(from)}, sessionId=$sessionId)"
    }

    companion object : StreamSerializer<Header>, ProtoSerializable<Header, Proto.Header> {
        override fun encode(stream: OutputStream, value: Header) {
            stream.writeByteArray(value.from.encoded)
            stream.writeByteArray(value.sessionId.toByteArray())
            stream.writeByteArray(value.signature.bytes)
        }

        override fun decode(stream: InputStream): Header {
            return Header(
                publicKeyFromBytes(stream.readByteArray()),
                UUIDByteArrayCodecCodec.decode(stream.readByteArray()),
                Signature(SIGNATURE_ALGO, stream.readByteArray())
            )
        }

        override fun toProto(value: Header): Relay.Header {
            return Relay.Header.newBuilder()
                .setFrom(ByteString.copyFrom(value.from.encoded))
                .setSessionId(ByteString.copyFrom(value.sessionId.toByteArray()))
                .setSignature(value.signature.toProto())
                .build()
        }

        override fun fromProto(value: Relay.Header): Header {
            return Header(
                publicKeyFromBytes(value.from.toByteArray()),
                UUIDByteArrayCodecCodec.decode(value.sessionId.toByteArray()),
                Signature.fromProto(value.signature)
            )
        }
    }
}