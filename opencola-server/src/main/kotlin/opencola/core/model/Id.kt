package opencola.core.model

import kotlinx.serialization.Serializable
import opencola.core.config.App
import opencola.core.security.sha256
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.toHexString
import opencola.core.serialization.ByteArrayCodec
import opencola.core.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.PublicKey

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

    override fun toString(): String {
        return bytes.toHexString()
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
        fun fromHexString(idAsHexString: String) : Id {
            return Id(idAsHexString.hexStringToByteArray())
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

