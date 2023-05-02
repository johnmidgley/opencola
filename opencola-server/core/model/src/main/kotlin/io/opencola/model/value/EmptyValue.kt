package io.opencola.model.value

class EmptyValue : Value<Any>(ByteArray(0)) {
    override fun equals(other: Any?): Boolean {
        return other is EmptyValue
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

val emptyValue = EmptyValue()
