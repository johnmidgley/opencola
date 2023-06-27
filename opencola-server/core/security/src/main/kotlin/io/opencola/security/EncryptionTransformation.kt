package io.opencola.security

import io.opencola.security.protobuf.Security as proto

enum class EncryptionTransformation(val transformationName: String, val protoValue: proto.EncryptionTransformation) {
    ECIES_WITH_AES_CBC("ECIESwithAES-CBC", proto.EncryptionTransformation.ECIES_WITH_AES_CBC);

    companion object {
        private val protoToEnumMap: Map<proto.EncryptionTransformation, EncryptionTransformation> = values().associateBy { it.protoValue }

        fun fromProto(protoValue: proto.EncryptionTransformation): EncryptionTransformation {
            return protoToEnumMap[protoValue] ?: throw IllegalArgumentException("Unknown proto value: $protoValue")
        }
    }
}