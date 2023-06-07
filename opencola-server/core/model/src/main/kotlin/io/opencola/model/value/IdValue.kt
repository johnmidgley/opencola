package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.model.protobuf.Model as Proto

class IdValue(value: Id) : Value<Id>(value) {
    companion object Factory : ValueWrapper<Id> {
        override fun encode(value: Id): ByteArray {
            return Id.encode(value)
        }

        override fun decode(value: ByteArray): Id {
            return Id.decode(value)
        }

        override fun toProto(value: Id): Proto.Value {
            return Proto.Value.newBuilder()
                .setOcType(Proto.Value.OCType.ID)
                .setBytes(ByteString.copyFrom(Id.encode(value)))
                .build()
        }

        override fun fromProto(value: Proto.Value): Id {
            require(value.ocType == Proto.Value.OCType.ID)
            return Id.decode(value.bytes.toByteArray())
        }

        override fun parseProto(bytes: ByteArray): Proto.Value {
            return Proto.Value.parseFrom(bytes)
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