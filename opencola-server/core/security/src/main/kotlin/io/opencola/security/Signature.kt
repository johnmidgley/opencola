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

import com.google.protobuf.ByteString
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.security.protobuf.Security as Proto
import io.opencola.util.Base58
import java.security.PrivateKey
import java.security.PublicKey

class Signature(val algorithm: SignatureAlgorithm, val bytes: ByteArray) {
    override fun toString(): String {
        return "Signature(algorithm=$algorithm, bytes=${Base58.encode(bytes)})"
    }

    fun encodeProto(): ByteArray {
        return encodeProto(this)
    }

    companion object : ProtoSerializable<Signature, Proto.Signature> {
        val none = Signature(SignatureAlgorithm.NONE, ByteArray(0))

        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return sign(privateKey, bytes).signature
        }

        override fun toProto(value: Signature): Proto.Signature {
            return Proto.Signature.newBuilder()
                .setAlgorithm(value.algorithm.protoValue)
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()
        }

        override fun fromProto(value: Proto.Signature): Signature {
            return Signature(SignatureAlgorithm.fromProto(value.algorithm), value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Signature {
            return Proto.Signature.parseFrom(bytes)
        }
    }

    fun isValidSignature(publicKey: PublicKey, bytes: ByteArray): Boolean {
        return isValidSignature(publicKey, bytes, this)
    }

    fun toProto(): Proto.Signature {
        return toProto(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Signature) return false

        if (algorithm != other.algorithm) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

fun Proto.Signature.toSignature(): Signature = Signature.fromProto(this)