package io.opencola.model.value

import io.opencola.serialization.codecs.StringByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class StringValue(value: String) : Value<String>(value) {
    companion object Wrapper : ValueWrapper<String> {
        override fun encode(value: String): ByteArray {
            return StringByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): String {
            return StringByteArrayCodec.decode(value)
        }

        override fun toProto(value: String): Proto.Value {
            return Proto.Value.newBuilder()
                .setString(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): String {
            require(value.dataCase == Proto.Value.DataCase.STRING)
            return value.string
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: String): Value<String> {
            return StringValue(value)
        }

        override fun unwrap(value: Value<String>): String {
            require(value is StringValue) { "Cannot unwrap ${value::class.simpleName} as StringValue" }
            return value.get()
        }
    }
}