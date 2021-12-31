package opencola.core.model

import java.io.InputStream
import java.io.OutputStream

interface ByteArrayStreamCodec<T> {
    // TODO: Should these just call the byte array codecs and wrap them with size markers?
    fun encode(stream: OutputStream, value: T)
    fun decode(stream: InputStream): T

    fun writeLong(stream: OutputStream, value: Long){
        stream.write(LongByteArrayCodec.encode(value))
    }

    fun readLong(stream: InputStream) : Long {
        return LongByteArrayCodec.decode(stream.readNBytes(Long.SIZE_BYTES))
    }

    fun writeByteArray(stream: OutputStream, byteArray: ByteArray){
        // TODO: Make this smart and use bytes, shorts, ints or longs depending on size
        stream.write(IntByteArrayCodec.encode(byteArray.size))
        stream.write(byteArray)
    }

    fun readByteArray(stream: InputStream) : ByteArray{
        return stream.readNBytes(IntByteArrayCodec.decode(stream.readNBytes(Int.SIZE_BYTES)))
    }
}