package io.opencola.security

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.protobuf.Security as Proto

class EncryptedBytes(val transformation: String, val bytes: ByteArray) {
    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<EncryptedBytes, Proto.EncryptedMessage> {
        override fun toProto(value: EncryptedBytes): Proto.EncryptedMessage {
            return Proto.EncryptedMessage.newBuilder()
                .setTransformation(value.transformation)
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()

        }

        override fun fromProto(value: Proto.EncryptedMessage): EncryptedBytes {
            return EncryptedBytes(value.transformation, value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.EncryptedMessage {
            return Proto.EncryptedMessage.parseFrom(bytes)
        }

    }
}
