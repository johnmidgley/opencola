package io.opencola.model.value

class EmptyValue : Value<Any>(ByteArray(0)) {
    override fun compareTo(other: Value<Any>): Int {
        if(other !is EmptyValue) return -1
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other is EmptyValue
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}

val emptyValue = EmptyValue()
