package opencola.core.model

import opencola.core.extensions.nullOrElse
import java.nio.ByteBuffer

// TODO: Can this be generified?
// TODO: Should this be ValueCodec?
interface ByteArrayCodec {
    fun encode(value: Any?): ByteArray
    fun decode(value: ByteArray?): Any?
}

// TODO - Move codecs from delegates here and add codec property to delegates that point to objects here
object LongByteArrayCodec : ByteArrayCodec {
    override fun encode(value: Any?): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value as Long).array()
    }

    override fun decode(value: ByteArray?): Long? {
        return value.nullOrElse { ByteBuffer.wrap(it).long }
    }
}