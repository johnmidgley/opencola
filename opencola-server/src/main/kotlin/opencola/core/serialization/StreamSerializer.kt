package opencola.core.serialization

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

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
}