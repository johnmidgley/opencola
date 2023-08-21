package io.opencola.model.value

import io.opencola.model.protobuf.Model

private val emptyBytes = ByteArray(0)

object EmptyValue : Value<Any>(emptyBytes) {
    val bytes = emptyBytes
    val proto = toProto()
    val encodedProto: ByteArray = proto.toByteArray()

    override fun equals(other: Any?): Boolean {
        return other is EmptyValue
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    private fun toProto(): Model.Value {
        return Model.Value.newBuilder()
            .setOcType(Model.Value.OCType.EMPTY)
            .build()
    }
}
