package opencola.core.serialization

import java.io.InputStream
import java.io.OutputStream
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