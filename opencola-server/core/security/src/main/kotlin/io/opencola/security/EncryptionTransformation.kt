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

import io.opencola.security.protobuf.Security as Proto

enum class EncryptionTransformation(val transformationName: String, val protoValue: Proto.EncryptedBytes.Transformation) {
    NONE("NONE", Proto.EncryptedBytes.Transformation.NONE),
    ECIES_WITH_AES_CBC("ECIESwithAES-CBC", Proto.EncryptedBytes.Transformation.ECIES_WITH_AES_CBC),
    AES_CBC_PKCS5PADDING("AES/CBC/PKCS5Padding", Proto.EncryptedBytes.Transformation.AES_CBC_PKCS5PADDING);

    companion object {
        private val protoToEnumMap: Map<Proto.EncryptedBytes.Transformation, EncryptionTransformation> = values().associateBy { it.protoValue }

        fun fromProto(protoValue: Proto.EncryptedBytes.Transformation): EncryptionTransformation {
            return protoToEnumMap[protoValue] ?: throw IllegalArgumentException("Unknown proto value: $protoValue")
        }
    }
}