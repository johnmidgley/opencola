package io.opencola.model.value

import io.opencola.serialization.codecs.FloatByteArrayCodec

class FloatValue(value: Float) : Value<Float>(value) {
    companion object : ValueWrapper<Float> {
        override fun encode(value: Float): ByteArray {
            return FloatByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Float {
            return FloatByteArrayCodec.decode(value)
        }

        override fun toProto(value: Float): io.opencola.model.protobuf.Model.Value {
            return io.opencola.model.protobuf.Model.Value.newBuilder()
                .setOcType(io.opencola.model.ValueType.FLOAT.ordinal)
                .setFloat(value)
                .build()
        }

        override fun fromProto(value: io.opencola.model.protobuf.Model.Value): Float {
            require(value.ocType == io.opencola.model.ValueType.FLOAT.ordinal)
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

    override fun compareTo(other: Value<Float>): Int {
        if(other !is FloatValue) return -1
        return value.compareTo(other.value)
    }
}