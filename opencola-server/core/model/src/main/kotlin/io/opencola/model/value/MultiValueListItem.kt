package io.opencola.model.value

import java.util.*

// TODO: Make value wrapper and remove toValue, keyOf and fromValue
class MultiValueListItem<T>(val key: UUID, value: T) : Value<T>(value) {
    override fun equals(other: Any?): Boolean {
        if(this === other) return true
        if(other == null) return false
        if(other !is MultiValueListItem<*>) return false
        if(key != other.key) return false
        return this.asAnyValue() == other.asAnyValue()
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + key.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}