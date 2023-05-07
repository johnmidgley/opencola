package io.opencola.model.value

import io.opencola.model.value.ValueType.BOOLEAN
import io.opencola.serialization.protobuf.Model
import io.opencola.serialization.codecs.BooleanByteArrayCodec

// TODO: Is it possible to templatize these Value classes?
class BooleanValue(value: Boolean) : Value<Boolean>(value) {
    companion object : ValueWrapper<Boolean> {
        override fun encode(value: Boolean): ByteArray {
            return BooleanByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Boolean {
            return BooleanByteArrayCodec.decode(value)
        }

        override fun toProto(value: Boolean): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(BOOLEAN.ordinal)
                .setBool(value)
                .build()
        }

        override fun fromProto(value: Model.Value): Boolean {
            require(value.ocType == BOOLEAN.ordinal)
            return value.bool
        }

        override fun wrap(value: Boolean): Value<Boolean> {
            return BooleanValue(value)
        }

        override fun unwrap(value: Value<Boolean>): Boolean {
            require(value is BooleanValue)
            return value.get()
        }
    }
}