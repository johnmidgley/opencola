package io.opencola.network.message

import io.opencola.network.protobuf.Network as Proto
import io.opencola.security.SignedBytes
import io.opencola.serialization.protobuf.ProtoSerializable

class Envelope(val header: SignedBytes, val message: SignedBytes)  {
    companion object : ProtoSerializable<Envelope, Proto.Envelope> {
        override fun toProto(value: Envelope): Proto.Envelope {
            return Proto.Envelope.newBuilder()
                .setHeader(value.header.toProto())
                .setMessage(value.message.toProto())
                .build()
        }

        override fun fromProto(value: Proto.Envelope): Envelope {
            return Envelope(
                header = SignedBytes.fromProto(value.header),
                message = SignedBytes.fromProto(value.message)
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.Envelope {
            return Proto.Envelope.parseFrom(bytes)
        }
    }

    fun encodeProto() : ByteArray {
        return encodeProto(this)
    }
}