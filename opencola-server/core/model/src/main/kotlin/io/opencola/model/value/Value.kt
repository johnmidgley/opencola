package io.opencola.model.value

import kotlinx.serialization.Serializable
import io.opencola.serialization.*
import io.opencola.serialization.protobuf.ProtoSerializable
import java.io.InputStream
import java.io.OutputStream
import io.opencola.model.protobuf.Model as ProtoModel
import java.util.*

// TODO: Might not be needed. Take a look.
// TODO: Consider a ByteArraySerializable interface. Then allow encode / decode to be switched between OC/Proto

private val emptyByteArray = "".toByteArray()
interface ValueWrapper<T> : ByteArrayCodec<T>, ProtoSerializable<T, ProtoModel.Value> {
    fun wrap(value: T): Value<T>
    fun unwrap(value: Value<T>): T

    // Encode value with possible emptyValue
    fun encodeAny(value : Value<Any>) : ByteArray {
        if(value is EmptyValue) return emptyByteArray
        return encode(value.get() as T)
    }

    // Decode value with possible emptyValue
    fun decodeAny(value: ByteArray) : Value<Any> {
        if(value.isEmpty()) return emptyValue
        return wrap(decode(value)) as Value<Any>
    }

    // Encode a value compatible with legacy encoding
    fun encode(stream: OutputStream, value: Value<Any>) {
       val bytes = if (value is EmptyValue) emptyByteArray else encode(value.get() as T)
       stream.writeByteArray(bytes)
    }

    // Decode a value compatible with legacy encoding
    fun decode(stream: InputStream): Value<Any> {
        val bytes = stream.readByteArray()
        return if(bytes.isEmpty()) {
            emptyValue
        } else {
            wrap(decode(bytes)) as Value<Any>
        }
    }
}

// TODO: See if @Serializable can be added back to Transaction et. al
@Serializable
abstract class Value<T>(val value: T) {
    fun get(): T {
        return value
    }

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



