/*
 * Copyright 2024-2026 OpenCola
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

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.security.protobuf.Security as Proto

class EncryptionParameters(val type: Type, val value: ByteArray) {
    enum class Type(val typeName: String, val protoValue: Proto.EncryptionParameters.Type) {
        NONE("NONE", Proto.EncryptionParameters.Type.NONE),
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