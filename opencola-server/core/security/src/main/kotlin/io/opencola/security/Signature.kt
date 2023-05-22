package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Security as ProtoSecurity
import io.opencola.util.Base58
import java.security.PrivateKey
import java.security.PublicKey

class Signature(val algorithm: String, val bytes: ByteArray) {
    companion object : ProtoSerializable<Signature, ProtoSecurity.Signature> {
        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return sign(privateKey, bytes)
        }

        override fun toProto(value: Signature): ProtoSecurity.Signature {
            return ProtoSecurity.Signature.newBuilder()
                .setAlgorithm(value.algorithm)
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()
        }

        override fun fromProto(value: ProtoSecurity.Signature): Signature {
            return Signature(value.algorithm, value.bytes.toByteArray())
        }
    }

    fun isValidSignature(publicKey: PublicKey, bytes: ByteArray): Boolean {
        return isValidSignature(publicKey, bytes, this)
    }

    fun toProto(): ProtoSecurity.Signature {
        return toProto(this)
    }

    override fun toString(): String {
        return Base58.encode(bytes)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false

        if (algorithm != other.algorithm) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}