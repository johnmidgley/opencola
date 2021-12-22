package opencola.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import opencola.core.security.sha256
import opencola.core.extensions.hexStringToByteArray
import opencola.core.extensions.toHexString
import java.net.URI
import java.security.PublicKey

// TODO: Make all classes data classes
// TODO: Fact, SubjectiveFact, FactStore<T: Fact> - for both objective and subjective facts
// TODO: Custom field serializers - doesn't seem possible

// TODO: This is really just a typed value with specialized constructors. Why can't they share serialization code when they have the same properties and one derives from the other?
// Maybe aggregate rather than derive?
@Serializable(with = Id.IdAsStringSerializer::class)
class Id {
    private val value: ByteArray

    private constructor(decoder: Decoder){
        value = decoder.decodeString().hexStringToByteArray()
    }

    constructor(publicKey: PublicKey){
        value = sha256(publicKey.encoded)
    }

    constructor(uri: URI){
        value = sha256(uri.toString())
    }

    // TODO: Add constructor that takes stream so whole file doesn't need to be loaded
    // TODO: Think about a data object rather than ByteArray
    constructor(data: ByteArray) {
        value = sha256(data)
    }

    // Construct id from a serialized string value
    // TODO: This seems a bit wrong, as all other constructors hash the input
    constructor(id: String){
        value = id.hexStringToByteArray()
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

    // TODO: See if this can be pulled out and shared with Value
    object IdAsStringSerializer : KSerializer<Id> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Value", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Id) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): Id {
            return Id(decoder)
        }
    }
}

