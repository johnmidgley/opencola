package io.opencola.security

import io.opencola.security.protobuf.Security as proto

enum class SignatureAlgorithm(val algorithmName: String, val protoValue: proto.Signature.Algorithm) {
    NONE("NONE", proto.Signature.Algorithm.NONE),
    SHA3_256_WITH_ECDSA("SHA3-256withECDSA", proto.Signature.Algorithm.SHA3_256_WITH_ECDSA);

    companion object {
        private val protoToEnumMap: Map<proto.Signature.Algorithm, SignatureAlgorithm> = values().associateBy { it.protoValue }

        fun fromProto(protoValue: proto.Signature.Algorithm): SignatureAlgorithm {
            return protoToEnumMap[protoValue] ?: throw IllegalArgumentException("Unknown proto value: $protoValue")
        }

        private val nameToEnumMap: Map<String, SignatureAlgorithm> = values().associateBy { it.algorithmName }

        fun fromAlgorithmName(algorithmName: String): SignatureAlgorithm {
            return nameToEnumMap[algorithmName] ?: throw IllegalArgumentException("Unknown algorithm name: $algorithmName")
        }
    }
}