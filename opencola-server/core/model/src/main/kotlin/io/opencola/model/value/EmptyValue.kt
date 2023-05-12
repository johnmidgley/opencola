package io.opencola.model.value

import io.opencola.serialization.protobuf.Model

class EmptyValue : Value<Any>(ByteArray(0)) {
    override fun equals(other: Any?): Boolean {
        return other is EmptyValue
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    fun toProto(): Model.Value {
        return Model.Value.newBuilder()
            .setOcType(Model.OCType.EMPTY)
            .build()
    }
}

val emptyValue = EmptyValue()
val emptyValueProto = emptyValue.toProto()
val emptyValueProtoEncoded: ByteArray = emptyValueProto.toByteArray()
