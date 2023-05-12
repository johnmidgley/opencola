package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.Id
import io.opencola.serialization.protobuf.Model as ProtoModel

class IdValue(value: Id) : Value<Id>(value) {
    companion object Factory : ValueWrapper<Id> {
        override fun encode(value: Id): ByteArray {
            return Id.encode(value)
        }

        override fun decode(value: ByteArray): Id {
            return Id.decode(value)
        }

        override fun toProto(value: Id): ProtoModel.Value {
            return ProtoModel.Value.newBuilder()
                .setOcType(ProtoModel.OCType.ID)
                .setBytes(ByteString.copyFrom(Id.encode(value)))
                .build()
        }

        override fun fromProto(value: ProtoModel.Value): Id {
            require(value.ocType == ProtoModel.OCType.ID)
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