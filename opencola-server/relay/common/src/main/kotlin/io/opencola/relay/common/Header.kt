package io.opencola.relay.common

import io.opencola.model.Id
import io.opencola.security.Signature
import io.opencola.security.publicKeyFromBytes
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.codecs.UUIDByteArrayCodecCodec
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

    companion object : StreamSerializer<Header> {
        override fun encode(stream: OutputStream, value: Header) {
            stream.writeByteArray(value.from.encoded)
            stream.writeByteArray(value.sessionId.toByteArray())
            stream.writeByteArray(value.signature.bytes)
        }

        override fun decode(stream: InputStream): Header {
            return Header(
                publicKeyFromBytes(stream.readByteArray()),
                UUIDByteArrayCodecCodec.decode(stream.readByteArray()),
                Signature(stream.readByteArray())
            )
        }
    }
}