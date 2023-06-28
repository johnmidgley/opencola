package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.security.protobuf.Security as Proto
import io.opencola.serialization.protobuf.ProtoSerializable

class SignedBytes(val signature: Signature, val bytes: ByteArray) {
    companion object : ProtoSerializable<SignedBytes, Proto.SignedBytes> {
        override fun toProto(value: SignedBytes): Proto.SignedBytes {
            return Proto.SignedBytes.newBuilder()
                .setSignature(value.signature.toProto())
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()

        }

        override fun fromProto(value: Proto.SignedBytes): SignedBytes {
            return SignedBytes(Signature.fromProto(value.signature), value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.SignedBytes {
            return Proto.SignedBytes.parseFrom(bytes)
        }

    }

    fun toProto(): Proto.SignedBytes {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }
}