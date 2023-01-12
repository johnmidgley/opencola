package io.opencola.serialization.codecs

import io.opencola.serialization.ByteArrayCodec
import java.nio.ByteBuffer

object FloatByteArrayCodec : ByteArrayCodec<Float> {
    override fun encode(value: Float): ByteArray {
        return ByteBuffer.allocate(Float.SIZE_BYTES).putFloat(value).array()
    }

    override fun decode(value: ByteArray): Float {
        return ByteBuffer.wrap(value).float
    }
}