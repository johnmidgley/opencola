package io.opencola.model.value

import io.opencola.serialization.codecs.BooleanByteArrayCodec
import io.opencola.serialization.protobuf.Model as ProtoModel

// TODO: Is it possible to templatize these Value classes?
class BooleanValue(value: Boolean) : Value<Boolean>(value) {
    companion object : ValueWrapper<Boolean> {
        override fun encode(value: Boolean): ByteArray {
            return BooleanByteArrayCodec.encode(value)
        }

        override fun decode(value: ByteArray): Boolean {
            return BooleanByteArrayCodec.decode(value)
        }

        override fun toProto(value: Boolean): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(ProtoModel.OCType.BOOLEAN)
                .setBool(value)
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): Boolean {
            require(value.ocType == ProtoModel.OCType.BOOLEAN)
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