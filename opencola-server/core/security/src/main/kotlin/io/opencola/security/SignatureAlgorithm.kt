/*
 * Copyright 2024 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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