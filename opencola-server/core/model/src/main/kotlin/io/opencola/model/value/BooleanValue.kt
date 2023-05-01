package io.opencola.model.value

import io.opencola.model.ValueType
import io.opencola.model.protobuf.Model
import io.opencola.serialization.readBoolean
import io.opencola.serialization.writeBoolean
import java.io.InputStream
import java.io.OutputStream

class BooleanValue(value: Boolean) : Value<Boolean>(value) {
    companion object : ValueWrapper<Boolean> {
        override fun encode(stream: OutputStream, value: Boolean) {
            stream.writeBoolean(value)
        }

        override fun decode(stream: InputStream): Boolean {
            return stream.readBoolean()
        }

        override fun toProto(value: Boolean): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(ValueType.BOOLEAN.ordinal)
                .setBool(value)
                .build()
        }

        override fun fromProto(value: Model.Value): Boolean {
            require(value.ocType == ValueType.BOOLEAN.ordinal)
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

    override fun compareTo(other: Value<Boolean>): Int {
        if(other !is BooleanValue) return -1
        return value.compareTo(other.value)
    }
}