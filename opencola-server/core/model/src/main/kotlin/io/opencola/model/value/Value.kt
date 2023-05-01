package io.opencola.model.value

import kotlinx.serialization.Serializable
import io.opencola.serialization.*
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.model.protobuf.Model as ProtoModel
import java.util.*

// TODO: Might not be needed. Take a look.
// TODO: Consider a ByteArraySerializable interface. Then allow encode / decode to be switched between OC/Proto
interface ValueWrapper<T> : StreamSerializer<T>, ProtoSerializable<T, ProtoModel.Value> {
    fun wrap(value: T): Value<T>
    fun unwrap(value: Value<T>): T
}

// TODO: See if @Serializable can be added back to Transaction et. al
@Serializable
abstract class Value<T>(val value: T) : Comparable<Value<T>> {
    fun get(): T {
        return value
    }

    fun asAnyValue() : Value<Any> {
        return this as Value<Any>
    }

    override fun equals(other: Any?): Boolean {
        if(other == null) return false
        if (this === other) return true
        if (other !is Value<*>) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

// TODO: Make value wrapper and remove toValue, keyOf and fromValue
class MultiValueListItem<T>(val key: UUID, value: T) : Value<T>(value) {
    override fun compareTo(other: Value<T>): Int {
        if(other !is MultiValueListItem<T>) return 1
        return key.compareTo(other.key)
    }

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



