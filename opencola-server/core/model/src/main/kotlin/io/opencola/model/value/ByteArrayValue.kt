package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.ValueType
import io.opencola.serialization.codecs.BytesByteArrayCodec
import io.opencola.model.protobuf.Model as ProtoModel

class ByteArrayValue(value: ByteArray) : Value<ByteArray>(value) {
    companion object : ValueWrapper<ByteArray> {
        override fun encode(value: ByteArray): ByteArray {
            return BytesByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): ByteArray {
            return BytesByteArrayCodec.decode(value)
        }

        override fun toProto(value: ByteArray): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(ValueType.BYTES.ordinal)
                .setBytes(ByteString.copyFrom(value))
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): ByteArray {
            require(value.ocType == ValueType.BYTES.ordinal)
            return value.bytes.toByteArray()
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