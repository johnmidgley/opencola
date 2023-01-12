package io.opencola.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.opencola.serialization.ByteArrayCodec
import io.opencola.serialization.StreamSerializer
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

enum class AttributeType {
    SingleValue,
    MultiValueSet,
    MultiValueList,
}

@Serializable(with = Attribute.AttributeAsStringSerializer::class)
// TODO: Make Attribute<T>
data class Attribute(
    val name: String,
    val uri: URI,
    val type: AttributeType,
    val codec: ByteArrayCodec<Any>,
    val isIndexable: Boolean,
    val computeFacts: ((Iterable<Fact>) -> Iterable<Fact>)?
) {
    constructor(uri: URI, type: AttributeType, codec: ByteArrayCodec<Any>, isIndexable: Boolean, computeFacts: ((Iterable<Fact>) -> Iterable<Fact>)? = null) :
            this(uri.path.split("/").last(), uri, type, codec, isIndexable, computeFacts)

    companion object Factory : StreamSerializer<Attribute> {
        override fun encode(stream: OutputStream, value: Attribute) {
            val ordinal = CoreAttribute.values().firstOrNull { it.spec == value }?.ordinal
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