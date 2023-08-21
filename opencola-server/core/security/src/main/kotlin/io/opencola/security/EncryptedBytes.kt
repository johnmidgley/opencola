package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.security.protobuf.Security as Proto

class EncryptedBytes(
    val transformation: EncryptionTransformation,
    val parameters: EncryptionParameters,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (other !is EncryptedBytes) return false
        if (transformation != other.transformation) return false
        if (parameters != other.parameters) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = transformation.hashCode()
        result = 31 * result + parameters.hashCode()
        result = 31 * result + bytes.contentHashCode()

        return result
    }

    fun toProto(): Proto.EncryptedBytes {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<EncryptedBytes, Proto.EncryptedBytes> {
        override fun toProto(value: EncryptedBytes): Proto.EncryptedBytes {
            return Proto.EncryptedBytes.newBuilder()
                .setTransformation(value.transformation.protoValue)
                .setBytes(ByteString.copyFrom(value.bytes))
                .setParameters(value.parameters.toProto())
                .build()

        }

        override fun fromProto(value: Proto.EncryptedBytes): EncryptedBytes {
            return EncryptedBytes(
                EncryptionTransformation.fromProto(value.transformation),
                EncryptionParameters.fromProto(value.parameters),
                value.bytes.toByteArray(),
            )
        }

        override fun parseProto(bytes: ByteArray): Proto.EncryptedBytes {
            return Proto.EncryptedBytes.parseFrom(bytes)
        }
    }
}

fun Proto.EncryptedBytes.toEncryptedBytes(): EncryptedBytes = EncryptedBytes.fromProto(this)