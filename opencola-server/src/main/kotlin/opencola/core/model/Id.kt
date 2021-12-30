package opencola.core.model

import kotlinx.serialization.Serializable
import opencola.core.security.sha256
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.nullOrElse
import opencola.core.extensions.toHexString
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
private const val valueSize = 32 // 32 bytes for a sha256 hash

@Serializable
data class Id(private val value: ByteArray) {
    init{
        assert(value.size == valueSize) { "Invalid id - size = ${value.size} but should be $valueSize" }
    }

    override fun toString(): String {
        return value.toHexString()
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return if(other is Id)
            return value.contentEquals(other.value)
        else
            false
    }

    companion object Factory : ByteArrayCodec, ByteArrayStreamCodec<Id> {
        // Construct id from a serialized string value
        // TODO: Should this be here?
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

        override fun encode(id: Any?): ByteArray {
             return (id as Id).value
        }

        override fun decode(value: ByteArray?): Any? {
             return value.nullOrElse { Id(it) }
        }

        override fun encode(stream: OutputStream, id: Id): OutputStream {
            stream.write(id.value)
            return stream
        }

        override fun decode(stream: InputStream): Id {
            return Id(stream.readNBytes(valueSize))
        }
    }
}

