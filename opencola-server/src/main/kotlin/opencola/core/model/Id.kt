package opencola.core.model

import io.ktor.util.*
import kotlinx.serialization.Serializable
import opencola.core.content.Base58
import opencola.core.extensions.toByteArray
import opencola.core.security.sha256
import opencola.core.serialization.ByteArrayCodec
import opencola.core.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.PublicKey
import java.util.*

// TODO: Make all classes data classes
// TODO: Fact, SubjectiveFact, FactStore<T: Fact> - for both objective and subjective facts
// TODO: Custom field serializers - doesn't seem possible

// TODO: This is really just a typed value with specialized constructors. Why can't they share serialization code when they have the same properties and one derives from the other?
// Maybe aggregate rather than derive?
// TODO: Make data class
// TODO: Is 16 bytes sufficient?
// TODO: length should come from MessageDigest provider
private val idLengthInBytes = sha256("").size

@Serializable
data class Id(private val bytes: ByteArray) {
    init{
        assert(bytes.size == idLengthInBytes) { "Invalid id - size = ${bytes.size} but should be $idLengthInBytes" }
    }

    fun encode(): String {
        return Base58.encode(bytes)
    }

    override fun toString(): String {
        return encode()
    }

    // Add tests for hashcode and equals for all domain objects
    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if(other is Id)
            return bytes.contentEquals(other.bytes)
        else
            false
    }

    companion object Factory : ByteArrayCodec<Id>, StreamSerializer<Id> {
        fun decode(value: String): Id {
            return decode(
                when (value.length) {
                    64 -> hex(value)
                    44 -> Base58.decode(value)
                    else -> throw IllegalArgumentException("Unknown Id encoding length: ${value.length}")
                }
            )
        }

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
            return Id(stream.readNBytes(idLengthInBytes))
        }
    }
}

