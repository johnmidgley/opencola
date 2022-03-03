package opencola.core.serialization

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

//TODO: When serialization is stable, migrate to custom format serialization
// https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/formats.md#custom-formats-experimental
interface StreamSerializer<T> {
    // TODO: Should these just call the byte array codecs and wrap them with size markers?
    fun encode(stream: OutputStream, value: T)
    fun decode(stream: InputStream): T

    // TODO: This doesn't feel right here - as it's not for streams. Maybe this should just be Serializer?
    fun encode(value: T) : ByteArray {
        return ByteArrayOutputStream().use {
            encode(it, value)
            it.toByteArray()
        }
    }

    fun writeInt(stream: OutputStream, value: Int){
        stream.write(IntByteArrayCodec.encode(value))
    }

    fun readInt(stream: InputStream) : Int {
        return IntByteArrayCodec.decode(stream.readNBytes(Int.SIZE_BYTES))
    }

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