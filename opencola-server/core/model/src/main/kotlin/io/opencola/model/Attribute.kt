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
import io.opencola.serialization.protobuf.Model
import io.opencola.serialization.protobuf.ProtoSerializable
import mu.KotlinLogging
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

    companion object : StreamSerializer<Attribute>, ProtoSerializable<Attribute?, ProtoModel.Attribute> {
        private val logger = KotlinLogging.logger("Attribute")

        override fun encode(stream: OutputStream, value: Attribute) {
            val ordinal = Attributes.getAttributeOrdinal(value)
                ?: throw NotImplementedError("Attempt to encode Attribute not in Attributes enum: ${value.uri}")
            stream.write(ordinal)
        }

        override fun decode(stream: InputStream): Attribute {
            val ordinal = stream.read()
            return CoreAttribute.values()[ordinal].spec
        }

        override fun toProto(value: Attribute?): ProtoModel.Attribute {
            require(value != null)
            return ProtoModel.Attribute.newBuilder().also {
                val ordinal = Attributes.getAttributeOrdinal(value)
                if(ordinal != null) {
                    it.setOrdinal(ordinal)
                } else {
                    it.setUri(value.uri.toString())
                }
            }.build()
        }

        override fun fromProto(value: ProtoModel.Attribute): Attribute? {
            return if(value.hasOrdinal()) {
                Attributes.getAttributeByOrdinal(value.ordinal).also {
                    if(it == null)
                        logger.error("Attempt to decode unknown attribute with ordinal: ${value.ordinal}")
                }
            } else if(value.hasUri()) {
                Attributes.getAttributeByUri(URI(value.uri)).also {
                    if(it == null)
                        logger.error("Attempt to decode unknown attribute with uri: ${value.uri}")
                }
            } else {
                logger.error("Attempt to decode attribute with no uri or ordinal")
                null
            }
        }

        override fun parseProto(bytes: ByteArray): Model.Attribute {
            return Model.Attribute.parseFrom(bytes)
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