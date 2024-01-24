/*
 * Copyright 2024 OpenCola
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

package io.opencola.serialization

import io.opencola.serialization.codecs.*
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.*

fun OutputStream.writeBoolean(value: Boolean) {
    write(BooleanByteArrayCodec.encode(value))
}

fun InputStream.readBoolean() : Boolean {
    return BooleanByteArrayCodec.decode(readNBytes(1))
}

fun OutputStream.writeInt(value: Int){
    write(IntByteArrayCodec.encode(value))
}

fun InputStream.readInt() : Int {
    return IntByteArrayCodec.decode(readNBytes(Int.SIZE_BYTES))
}

fun OutputStream.writeLong(value: Long){
    write(LongByteArrayCodec.encode(value))
}

fun InputStream.readLong() : Long {
    return LongByteArrayCodec.decode(readNBytes(Long.SIZE_BYTES))
}

fun OutputStream.writeFloat(value: Float){
    write(FloatByteArrayCodec.encode(value))
}

fun InputStream.readFloat() : Float {
    return FloatByteArrayCodec.decode(readNBytes(Float.SIZE_BYTES))
}


fun OutputStream.writeUUID(value: UUID){
    write(UUIDByteArrayCodecCodec.encode(value))
}

fun InputStream.readUUID() : UUID {
    return UUIDByteArrayCodecCodec.decode(readNBytes(Long.SIZE_BYTES * 2))
}

fun OutputStream.writeByteArray(byteArray: ByteArray){
    // TODO: Make this smart and use bytes, shorts, ints or longs depending on size
    write(IntByteArrayCodec.encode(byteArray.size))
    write(byteArray)
}

fun InputStream.readByteArray() : ByteArray{
    return readNBytes(IntByteArrayCodec.decode(readNBytes(Int.SIZE_BYTES)))
}

fun OutputStream.writeString(string: String){
    writeByteArray(string.toByteArray())
}

fun InputStream.readString() : String {
    return String(readByteArray())
}

fun OutputStream.writeUri(uri: URI){
    writeString(uri.toString())
}

fun InputStream.readUri() : URI {
    return URI(readString())
}
