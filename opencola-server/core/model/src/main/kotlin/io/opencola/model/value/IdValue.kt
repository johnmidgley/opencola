package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.model.ValueType
import io.opencola.model.protobuf.Model
import java.io.InputStream
import java.io.OutputStream

class IdValue(value: Id) : Value<Id>(value) {
    companion object Factory : ValueWrapper<Id> {
        override fun encode(stream: OutputStream, value: Id) {
            Id.encode(stream, value)
        }

        override fun decode(stream: InputStream): Id {
            return Id.decode(stream)
        }

        override fun toProto(value: Id): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(ValueType.ID.ordinal)
                .setBytes(ByteString.copyFrom(Id.encode(value)))
                .build()
        }

        override fun fromProto(value: Model.Value): Id {
            require(value.ocType == ValueType.ID.ordinal)
            return Id.decode(value.bytes.toByteArray())
        }

        override fun wrap(value: Id): Value<Id> {
            return IdValue(value)
        }

        override fun unwrap(value: Value<Id>): Id {
            require(value is IdValue)
            return value.get()
        }
    }

    override fun compareTo(other: Value<Id>): Int {
        if(other !is IdValue) return -1
        return value.compareTo(other.value)
    }
}