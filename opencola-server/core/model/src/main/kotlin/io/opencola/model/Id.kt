package io.opencola.model

import com.google.protobuf.ByteString
import io.opencola.model.protobuf.Model as Proto
import kotlinx.serialization.Serializable
import io.opencola.security.sha256
import io.opencola.serialization.ByteArrayCodec
import io.opencola.serialization.StreamSerializer
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.util.*
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.PublicKey
import java.util.*

@Serializable
data class Id(private val bytes: ByteArray) : Comparable<Id> {
    init{
        assert(bytes.size == lengthInBytes) { "Invalid id - size = ${bytes.size} but should be $lengthInBytes" }
    }

    fun encoded(): ByteArray {
        return bytes
    }

    fun legacyEncodeToString() : String {
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
        return if(other is Id)
            return bytes.contentEquals(other.bytes)
        else
            false
    }

    fun toProto() : Proto.Id {
        return Factory.toProto(this)
    }

    fun encodeProto() : ByteArray {
        return toProto().toByteArray()
    }

    companion object Factory : ByteArrayCodec<Id>, StreamSerializer<Id>, ProtoSerializable<Id, Proto.Id> {
        val lengthInBytes = sha256("").size

        // TODO: Should return Id? - empty string is not valid.
        fun decode(value: String): Id {
            return decode(
                when (value.length) {
                    64 -> value.hexStringToByteArray()
                    else -> Base58.decode(value)
                }
            )
        }

        // IMPORTANT: If ids change, then data cannot be joined together properly. If any new of* methods are added,
        // make sure to add corresponding stability tests.

        // TODO: Convert of* methods to extension methods?
        fun ofPublicKey(publicKey: PublicKey) : Id {
            return Id(sha256(publicKey.encoded))
        }

        fun ofUri(uri: URI) : Id {
             return Id(sha256(uri.toString().toByteArray()))
        }

        // TODO: Add constructor that takes stream so whole file doesn't need to be loaded
        // TODO: Think about a data object rather than ByteArray
        fun ofData(data: ByteArray) : Id {
            return Id(sha256(data))
        }

        fun new() : Id {
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
            return Id(stream.readNBytes(lengthInBytes))
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

fun Proto.Id.toId() : Id {
    return Id.fromProto(this)
}