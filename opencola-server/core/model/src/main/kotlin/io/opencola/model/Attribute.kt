package io.opencola.model

import io.opencola.model.value.ValueWrapper
import io.opencola.serialization.protobuf.Model as ProtoModel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
    val valueWrapper: ValueWrapper<Any>,
    val isIndexable: Boolean,
    val computeFacts: ((Iterable<Fact>) -> Iterable<Fact>)?
) {
    constructor(uri: URI, type: AttributeType, valueWrapper: ValueWrapper<Any>, isIndexable: Boolean, computeFacts: ((Iterable<Fact>) -> Iterable<Fact>)? = null) :
            this(uri.path.split("/").last(), uri, type, valueWrapper, isIndexable, computeFacts)

    companion object Factory : StreamSerializer<Attribute> {
        override fun encode(stream: OutputStream, value: Attribute) {
            val ordinal = Attributes.getAttributeOrdinal(value)
                ?: throw NotImplementedError("Attempt to encode Attribute not in Attributes enum: ${value.uri}")
            stream.write(ordinal)
        }

        override fun decode(stream: InputStream): Attribute {
            val ordinal = stream.read()
            return CoreAttribute.values()[ordinal].spec
        }

        fun toProto(attribute: Attribute): ProtoModel.Attribute {
            return ProtoModel.Attribute.newBuilder().also {
                val ordinal = Attributes.getAttributeOrdinal(attribute)
                if(ordinal != null) {
                    it.setOrdinal(ordinal)
                } else {
                    it.setUri(attribute.uri.toString())
                }
            }.build()
        }

        fun fromProto(attribute: ProtoModel.Attribute): Attribute {
            return if(attribute.hasOrdinal()) {
                CoreAttribute.values()[attribute.ordinal].spec
            } else if(attribute.hasUri()) {
                Attributes.getAttributeByUri(URI(attribute.uri))
                    ?: throw RuntimeException("Attempt to decode unknown attribute: ${attribute.uri}")
            } else {
                throw RuntimeException("Attempt to decode attribute with no uri or ordinal")
            }
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
            return Attributes.getAttributeByUri(uri)
                ?: throw RuntimeException("Attempt to decode unknown attribute: $uri")
        }
    }
}