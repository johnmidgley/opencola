package io.opencola.model.value

import io.opencola.model.ValueType
import io.opencola.model.protobuf.Model as ProtoModel
import io.opencola.serialization.readString
import io.opencola.serialization.writeString
import java.io.InputStream
import java.io.OutputStream

class StringValue(value: String) : Value<String>(value) {
    companion object Wrapper : ValueWrapper<String> {
        override fun encode(stream: OutputStream, value: String) {
            stream.writeString(value)
        }

        override fun decode(stream: InputStream): String {
            return stream.readString()
        }

        override fun toProto(value: String): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(ValueType.STRING.ordinal)
                .setString(value)
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): String {
            require(value.ocType == ValueType.STRING.ordinal)
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

    override fun compareTo(other: Value<String>): Int {
        if(other !is StringValue) return -1
        return value.compareTo(other.value)
    }
}