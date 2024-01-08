package io.opencola.model.value

// TODO: Make consistent with how Events are modeled (i.e. this is a RawValue and ValueWrapper is just a Value)
//  Maybe make a ValueConverter that you can register value types with and ValueWrapper would just then be a codec?
abstract class Value<T>(val value: T) {
    fun get(): T {
        return value
    }

    @Suppress("UNCHECKED_CAST")
    fun asAnyValue() : Value<Any> {
        return this as Value<Any>
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this === other) return true
        if (other !is Value<*>) return false
        if (javaClass != other.javaClass) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}



