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
import io.opencola.security.protobuf.Security as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import java.security.PublicKey

class SignedBytes(val signature: Signature, val bytes: ByteArray) {
    companion object : ProtoSerializable<SignedBytes, Proto.SignedBytes> {
        override fun toProto(value: SignedBytes): Proto.SignedBytes {
            return Proto.SignedBytes.newBuilder()
                .setSignature(value.signature.toProto())
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()

        }

        override fun fromProto(value: Proto.SignedBytes): SignedBytes {
            return SignedBytes(Signature.fromProto(value.signature), value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.SignedBytes {
            return Proto.SignedBytes.parseFrom(bytes)
        }

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedBytes) return false

        if (signature != other.signature) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signature.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    fun toProto(): Proto.SignedBytes {
        return toProto(this)
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    fun validate(publicKey: PublicKey): Boolean {
        return isValidSignature(publicKey, bytes, signature)
    }
}