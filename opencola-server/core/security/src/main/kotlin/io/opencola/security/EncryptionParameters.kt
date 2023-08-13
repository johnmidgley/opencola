package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.security.protobuf.Security as Proto

class EncryptionParameters(val type: Type, val value: ByteArray) {
    enum class Type(val typeName: String, val protoValue: Proto.EncryptionParameters.Type) {
        IES("IES", Proto.EncryptionParameters.Type.IES),
        IV("IV", Proto.EncryptionParameters.Type.IV);

        companion object {
            private val protoToEnumMap: Map<Proto.EncryptionParameters.Type, Type> = values().associateBy { it.protoValue }

            fun fromProto(protoValue: Proto.EncryptionParameters.Type): Type {
                return protoToEnumMap[protoValue] ?: throw IllegalArgumentException("Unknown proto value: $protoValue")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is EncryptionParameters) return false
        if (type != other.type) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.contentHashCode()
        return result
    }

    fun toProto() : Proto.EncryptionParameters {
        return toProto(this)
    }

    companion object : ProtoSerializable<EncryptionParameters, Proto.EncryptionParameters> {
        override fun toProto(value: EncryptionParameters): Proto.EncryptionParameters {
            return Proto.EncryptionParameters.newBuilder()
                .setType(value.type.protoValue)
                .setBytes(ByteString.copyFrom(value.value))
                .build()
        }

        override fun fromProto(value: Proto.EncryptionParameters): EncryptionParameters {
            return EncryptionParameters(Type.fromProto(value.type), value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.EncryptionParameters {
            return Proto.EncryptionParameters.parseFrom(bytes)
        }
    }
}