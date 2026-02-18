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

package io.opencola.model

import com.google.protobuf.ByteString
import io.opencola.security.hash.Sha256Hash
import io.opencola.model.protobuf.Model as Proto
import io.opencola.serialization.ByteArrayCodec
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.util.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.PublicKey
import java.util.*

// TODO: Change 'bytes' to 'encoded' and make public?
data class Id(private val bytes: ByteArray) : Comparable<Id> {
    init {
        require(bytes.size == LENGTH_IN_BYTES) { "Invalid id - size = ${bytes.size} but should be $LENGTH_IN_BYTES" }
    }

    fun encoded(): ByteArray {
        return bytes
    }

    fun legacyEncodeToString(): String {
        return bytes.toHexString()
    }

    override fun toString(): String {
        return Base58.encode(bytes)
    }

    // Add tests for hashcode and equals for all domain objects
    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun compareTo(other: Id): Int {
        return bytes.compareTo(other.bytes)
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Id)
            return bytes.contentEquals(other.bytes)
        else
            false
    }

    fun toProto(): Proto.Id {
        return Factory.toProto(this)
    }

    fun encodeProto(): ByteArray {
        return toProto().toByteArray()
    }

    companion object Factory : ByteArrayCodec<Id>, StreamSerializer<Id>, ProtoSerializable<Id, Proto.Id> {
        const val LENGTH_IN_BYTES = 32
        val EMPTY = Id(ByteArray(LENGTH_IN_BYTES))

        // TODO: Should return Id? - empty string is not valid.
        fun decode(value: String): Id {
            return decode(
                when (value.length) {
                    64 -> value.hexStringToByteArray()
                    else -> Base58.decode(value)
                }
            )
        }

        fun tryDecode(value: String?): Id? {
            return try {
                if (value == null) null else decode(value)
            } catch (e: Exception) {
                null
            }
        }

        // TODO: Add constructor that takes stream so whole file doesn't need to be loaded
        // TODO: Think about a data object rather than ByteArray
        fun ofData(data: ByteArray): Id {
            return Id(Sha256Hash.ofBytes(data).bytes)
        }

        // TODO: Convert of* methods to extension methods?
        fun ofPublicKey(publicKey: PublicKey): Id {
            return ofData(publicKey.encoded)
        }

        fun ofUri(uri: URI): Id {
            return ofData(uri.toString().toByteArray())
        }

        fun new(): Id {
            return ofData(UUID.randomUUID().toByteArray())
        }

        override fun encode(value: Id): ByteArray {
            return value.bytes
        }

        override fun decode(value: ByteArray): Id {
            return Id(value)
        }

        override fun encode(stream: OutputStream, value: Id) {
            stream.write(value.bytes)
        }

        override fun decode(stream: InputStream): Id {
            return Id(stream.readNBytes(LENGTH_IN_BYTES))
        }

        override fun toProto(value: Id): Proto.Id {
            return Proto.Id.newBuilder()
                .setBytes(ByteString.copyFrom(value.bytes))
                .build()
        }

        override fun fromProto(value: Proto.Id): Id {
            return Id(value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Id {
            return Proto.Id.parseFrom(bytes)
        }
    }
}

fun Proto.Id.toId(): Id {
    return Id.fromProto(this)
}

// This is separated out from Id, so that it can be used contextually. If it were defined in the companion object, it would
// force projects dependent on Id to also depend on the kotlinx serialization library.
object IdAsStringSerializer : KSerializer<Id> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("id", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Id) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Id {
        return Id.decode(decoder.decodeString())
    }
}