package io.opencola.model.value

import io.opencola.serialization.codecs.FloatByteArrayCodec
import io.opencola.model.protobuf.Model as Proto

class FloatValue(value: Float) : Value<Float>(value) {
    companion object : ValueWrapper<Float> {
        override fun encode(value: Float): ByteArray {
            return FloatByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Float {
            return FloatByteArrayCodec.decode(value)
        }

        override fun toProto(value: Float): Proto.Value {
            return Proto.Value.newBuilder()
                .setOcType(Proto.Value.OCType.FLOAT)
                .setFloat(value)
                .build()
        }

        override fun fromProto(value: Proto.Value): Float {
            require(value.ocType == Proto.Value.OCType.FLOAT)
            return value.float
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
        }

        override fun wrap(value: Float): Value<Float> {
            return FloatValue(value)
        }

        override fun unwrap(value: Value<Float>): Float {
            require(value is FloatValue)
            return value.get()
        }
    }
}