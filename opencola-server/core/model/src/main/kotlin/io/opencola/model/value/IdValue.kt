package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.model.ValueType.*
import io.opencola.serialization.protobuf.Model

class IdValue(value: Id) : Value<Id>(value) {
    companion object Factory : ValueWrapper<Id> {
        override fun encode(value: Id): ByteArray {
            return Id.encode(value)
        }

        override fun decode(value: ByteArray): Id {
            return Id.decode(value)
        }

        override fun toProto(value: Id): Model.Value {
            return Model.Value.newBuilder()
                .setOcType(ID.ordinal)
                .setBytes(ByteString.copyFrom(Id.encode(value)))
                .build()
        }

        override fun fromProto(value: Model.Value): Id {
            require(value.ocType == ID.ordinal)
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
}