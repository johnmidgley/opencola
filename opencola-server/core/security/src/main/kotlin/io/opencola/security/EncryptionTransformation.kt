package io.opencola.security

import io.opencola.security.protobuf.Security as Proto

enum class EncryptionTransformation(val transformationName: String, val protoValue: Proto.EncryptedBytes.Transformation) {
    ECIES_WITH_AES_CBC("ECIESwithAES-CBC", Proto.EncryptedBytes.Transformation.ECIES_WITH_AES_CBC),
    AES_CBC_PKCS5PADDING("AES/CBC/PKCS5Padding", Proto.EncryptedBytes.Transformation.AES_CBC_PKCS5PADDING);

    companion object {
        private val protoToEnumMap: Map<Proto.EncryptedBytes.Transformation, EncryptionTransformation> = values().associateBy { it.protoValue }

        fun fromProto(protoValue: Proto.EncryptedBytes.Transformation): EncryptionTransformation {
            return protoToEnumMap[protoValue] ?: throw IllegalArgumentException("Unknown proto value: $protoValue")
        }
    }
}