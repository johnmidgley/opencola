package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.security.protobuf.Security as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedBytes) return false

        if (signature != other.signature) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signature.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    fun toProto(): Proto.SignedBytes {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    fun validate(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, bytes, signature)
    }
}