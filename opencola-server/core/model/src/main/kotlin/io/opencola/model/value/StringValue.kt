package io.opencola.model.value

import io.opencola.model.value.ValueType.STRING
import io.opencola.serialization.codecs.StringByteArrayCodec
import io.opencola.serialization.protobuf.Model as ProtoModel

class StringValue(value: String) : Value<String>(value) {
    companion object Wrapper : ValueWrapper<String> {
        override fun encode(value: String): ByteArray {
            return StringByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): String {
            return StringByteArrayCodec.decode(value)
        }

        override fun toProto(value: String): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(STRING.ordinal)
                .setString(value)
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): String {
            require(value.ocType == STRING.ordinal)
            return value.string
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