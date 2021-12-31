package opencola.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

@Serializable(with = Attribute.AttributeAsStringSerializer::class)
data class Attribute(val name: String, val uri: URI, val codec: ByteArrayCodec<Any>, val isIndexable: Boolean){
    constructor(uri: URI, codec: ByteArrayCodec<Any>, isIndexable: Boolean) : this(uri.path.split("/").last(), uri, codec, isIndexable)

    companion object Factory : ByteArrayStreamCodec<Attribute>{
        override fun encode(stream: OutputStream, value: Attribute){
            val ordinal = CoreAttribute.values().firstOrNull{ it.spec == value }?.ordinal
                ?: throw NotImplementedError("Attempt to encode Attribute not in Attributes enum: ${value.uri}")
            stream.write(ordinal)
        }

        override fun decode(stream: InputStream): Attribute {
            val ordinal = stream.read()
            return CoreAttribute.values().firstOrNull { it.ordinal == ordinal }?.spec
                ?: throw RuntimeException("Attempt to decode attribute with invalid ordinal: $ordinal")
        }
    }

    object AttributeAsStringSerializer : KSerializer<Attribute> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("uri", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Attribute) {
            encoder.encodeString(value.uri.toString())
        }

        override fun deserialize(decoder: Decoder): Attribute {
            val uri = URI(decoder.decodeString())
            // TODO: Won't work with dynamic attributes (i.e. outside of Attributes enum)
            return CoreAttribute.values().firstOrNull { it.spec.uri == uri }?.spec
                ?: throw RuntimeException("Attempt to decode unknown attribute: $uri")
        }
    }
}