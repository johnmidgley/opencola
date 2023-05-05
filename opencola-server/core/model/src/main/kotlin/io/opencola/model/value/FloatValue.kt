package io.opencola.model.value

import io.opencola.model.ValueType.FLOAT
import io.opencola.serialization.codecs.FloatByteArrayCodec
import io.opencola.serialization.protobuf.Model as ProtoModel

class FloatValue(value: Float) : Value<Float>(value) {
    companion object : ValueWrapper<Float> {
        override fun encode(value: Float): ByteArray {
            return FloatByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Float {
            return FloatByteArrayCodec.decode(value)
        }

        override fun toProto(value: Float): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(FLOAT.ordinal)
                .setFloat(value)
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): Float {
            require(value.ocType == FLOAT.ordinal)
            return value.float
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