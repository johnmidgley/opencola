package io.opencola.model.value

import io.opencola.serialization.codecs.IntByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class IntValue(value: Int) : Value<Int>(value) {
    companion object : ValueWrapper<Int> {
        override fun encode(value: Int): ByteArray {
            return IntByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Int {
            return IntByteArrayCodec.decode(value)
        }

        override fun toProto(value: Int): Proto.Value {
            return Proto.Value.newBuilder()
                .setInt(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): Int {
            require(value.dataCase == Proto.Value.DataCase.INT)
            return value.int
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: Int): Value<Int> {
            return IntValue(value)
        }

        override fun unwrap(value: Value<Int>): Int {
            require(value is IntValue)
            return value.get()
        }
    }
}