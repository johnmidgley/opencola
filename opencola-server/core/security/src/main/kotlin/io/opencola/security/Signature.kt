package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Security as Proto
import io.opencola.util.Base58
import java.security.PrivateKey
import java.security.PublicKey

class Signature(val algorithm: String, val bytes: ByteArray) {
    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<Signature, Proto.Signature> {
        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return sign(privateKey, bytes)
        }

        override fun toProto(value: Signature): Proto.Signature {
            return Proto.Signature.newBuilder()
                .setAlgorithm(value.algorithm)
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()
        }

        override fun fromProto(value: Proto.Signature): Signature {
            return Signature(value.algorithm, value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Signature {
            return Proto.Signature.parseFrom(bytes)
        }
    }

    fun isValidSignature(publicKey: PublicKey, bytes: ByteArray): Boolean {
        return isValidSignature(publicKey, bytes, this)
    }

    fun toProto(): Proto.Signature {
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