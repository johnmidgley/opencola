package io.opencola.model.value

import io.opencola.serialization.ByteArrayCodec
import io.opencola.serialization.protobuf.Model as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream

private val emptyByteArray = "".toByteArray()

// TODO: Might not be needed. Take a look.
// TODO: Consider a ByteArraySerializable interface. Then allow encode / decode to be switched between OC/Proto
interface ValueWrapper<T> : ByteArrayCodec<T>, ProtoSerializable<T, Proto.Value> {
    fun wrap(value: T): Value<T>
    fun unwrap(value: Value<T>): T

    // TODO: Remove encodeAny and decodeAny after migration

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

    fun encodeProtoAny(value: Value<Any>) : ByteArray {
        return if (value is EmptyValue)
            emptyValueProtoEncoded
        else
            toProto(value.get() as T).toByteArray()
    }

     fun decodeProtoAny(value: ByteArray) : Value<Any> {
        Proto.Value.parseFrom(value).let {
            return if(it.ocType == Proto.OCType.EMPTY)
                emptyValue
            else
                wrap(fromProto(it)) as Value<Any>
        }
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