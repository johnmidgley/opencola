package io.opencola.core.serialization

import io.opencola.core.serialization.codecs.IntByteArrayCodec
import io.opencola.core.serialization.codecs.LongByteArrayCodec
import io.opencola.core.serialization.codecs.UUIDByteArrayCodecCodec
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.*

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
