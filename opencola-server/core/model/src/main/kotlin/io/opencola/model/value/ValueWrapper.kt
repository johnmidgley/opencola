/*
 * Copyright 2024-2026 OpenCola
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.opencola.model.value

import io.opencola.serialization.ByteArrayCodec
import io.opencola.model.protobuf.Model as Proto
import io.opencola.serialization.protobuf.ProtoSerializable
import io.opencola.serialization.readByteArray
import io.opencola.serialization.writeByteArray
import java.io.InputStream
import java.io.OutputStream

// TODO: Might not be needed. Take a look.
// TODO: Consider a ByteArraySerializable interface. Then allow encode / decode to be switched between OC/Proto
@Suppress("UNCHECKED_CAST")
interface ValueWrapper<T> : ByteArrayCodec<T>, ProtoSerializable<T, Proto.Value> {
    fun wrap(value: T): Value<T>
    fun unwrap(value: Value<T>): T

    // TODO: Remove encodeAny and decodeAny after migration

    // Encode value with possible emptyValue
    fun encodeAny(value : Value<Any>) : ByteArray {
        if(value is EmptyValue) return EmptyValue.bytes
        return encode(value.get() as T)
    }

    // Decode value with possible emptyValue
    fun decodeAny(value: ByteArray) : Value<Any> {
        if(value.isEmpty()) return EmptyValue
        return wrap(decode(value)) as Value<Any>
    }

    fun encodeProtoAny(value: Value<Any>) : ByteArray {
        return if (value is EmptyValue)
            EmptyValue.encodedProto
        else
            toProto(value.get() as T).toByteArray()
    }

     fun decodeProtoAny(value: ByteArray) : Value<Any> {
        Proto.Value.parseFrom(value).let {
            return if(it.dataCase == Proto.Value.DataCase.EMPTY)
                EmptyValue
            else
                wrap(fromProto(it)) as Value<Any>
        }
    }

    // Encode a value compatible with legacy encoding
    fun encode(stream: OutputStream, value: Value<Any>) {
        val bytes = if (value is EmptyValue) EmptyValue.bytes else encode(value.get() as T)
        stream.writeByteArray(bytes)
    }

    // Decode a value compatible with legacy encoding
    fun decode(stream: InputStream): Value<Any> {
        val bytes = stream.readByteArray()
        return if(bytes.isEmpty()) {
            EmptyValue
        } else {
            wrap(decode(bytes)) as Value<Any>
        }
    }
}