package io.opencola.model.value

import io.opencola.serialization.codecs.StringByteArrayCodec
import mu.KotlinLogging
import io.opencola.model.protobuf.Model as Proto

class StringValue(value: String) : Value<String>(value) {
    init {
        if(value.isBlank()) {
            // Storing / sending a blank string is not harmful, but wastes space, so should be avoided.
            logger.warn { "Strings should not be blank" }
        }
    }

    companion object Wrapper : ValueWrapper<String> {
        private val logger = KotlinLogging.logger("StringValue")

        override fun encode(value: String): ByteArray {
            return StringByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): String {
            return StringByteArrayCodec.decode(value)
        }

        override fun toProto(value: String): Proto.Value {
            return Proto.Value.newBuilder()
                .setOcType(Proto.Value.OCType.STRING)
                .setString(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): String {
            require(value.ocType == Proto.Value.OCType.STRING)
            return value.string
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: String): Value<String> {
            return StringValue(value)
        }

        override fun unwrap(value: Value<String>): String {
            require(value is StringValue)
            return value.get()
        }
    }
}