package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.serialization.codecs.BytesByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class ByteArrayValue(value: ByteArray) : Value<ByteArray>(value) {
    companion object : ValueWrapper<ByteArray> {
        override fun encode(value: ByteArray): ByteArray {
            return BytesByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): ByteArray {
            return BytesByteArrayCodec.decode(value)
        }

        override fun toProto(value: ByteArray): Proto.Value {
            return Proto.Value.newBuilder()
                .setBytes(ByteString.copyFrom(value))
                .build()
        }

        override fun fromProto(value: Proto.Value): ByteArray {
            require(value.dataCase == Proto.Value.DataCase.BYTES)
            return value.bytes.toByteArray()
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: ByteArray): Value<ByteArray> {
            return ByteArrayValue(value)
        }

        override fun unwrap(value: Value<ByteArray>): ByteArray {
            require(value is ByteArrayValue)
            return value.get()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ByteArrayValue) return false
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}