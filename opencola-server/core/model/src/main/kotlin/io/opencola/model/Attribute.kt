package io.opencola.model

import io.opencola.model.value.ValueWrapper
import io.opencola.model.protobuf.Model as Proto
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import io.opencola.serialization.StreamSerializer
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

// TODO: Make Attribute<T>
data class Attribute(
    val name: String,
    val uri: URI,
    val type: AttributeType,
    val protoAttribute: Proto.Attribute.CoreAttribute?,
    val valueWrapper: ValueWrapper<Any>,
    val isIndexable: Boolean,
    val computeFacts: ((Iterable<Fact>) -> Iterable<Fact>)?
) {
    constructor(
        uri: URI,
        type: AttributeType,
        protoAttribute: Proto.Attribute.CoreAttribute?,
        valueWrapper: ValueWrapper<Any>,
        isIndexable: Boolean,
        computeFacts: ((Iterable<Fact>) -> Iterable<Fact>)? = null
    ) :
            this(uri.path.split("/").last(), uri, type, protoAttribute, valueWrapper, isIndexable, computeFacts)

    companion object : StreamSerializer<Attribute>, ProtoSerializable<Attribute?, Proto.Attribute> {
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

        override fun toProto(value: Attribute?): Proto.Attribute {
            require(value != null)
            return Proto.Attribute.newBuilder().also {
                val protoAttribute = Attributes.getProtoCoreAttribute(value)
                if (protoAttribute != null) {
                    it.setCoreAttribute(protoAttribute)
                } else {
                    it.setUri(value.uri.toString())
                }
            }.build()
        }

        override fun fromProto(value: Proto.Attribute): Attribute? {
            return if (value.hasCoreAttribute()) {
                Attributes.getAttributeByProtoCoreAttribute(value.coreAttribute).also {
                    if (it == null)
                        logger.error("Attempt to decode unknown attribute with proto coreAttribute: ${value.coreAttribute}")
                }
            } else if (value.hasUri()) {
                Attributes.getAttributeByUri(URI(value.uri)).also {
                    if (it == null)
                        logger.error("Attempt to decode unknown attribute with uri: ${value.uri}")
                }
            } else {
                logger.error("Attempt to decode attribute with no uri or ordinal")
                null
            }
        }

        override fun parseProto(bytes: ByteArray): Proto.Attribute {
            return Proto.Attribute.parseFrom(bytes)
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