package io.opencola.model.value

import com.google.protobuf.ByteString
import io.opencola.model.protobuf.Model

private val emptyBytes = ByteArray(0)

object EmptyValue : Value<Any>(emptyBytes) {
    val bytes = emptyBytes
    private val byteString: ByteString = ByteString.copyFrom(emptyBytes)
    val proto = toProto()
    val encodedProto: ByteArray = proto.toByteArray()

    override fun toString(): String {
        return "EmptyValue"
    }

    override fun equals(other: Any?): Boolean {
        return other is EmptyValue
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    private fun toProto(): Model.Value {
        return Model.Value.newBuilder()
            .setEmpty(byteString)
            .build()
    }
}
