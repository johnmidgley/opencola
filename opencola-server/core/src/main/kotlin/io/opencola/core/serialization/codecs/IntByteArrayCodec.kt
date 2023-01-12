package io.opencola.core.serialization.codecs

import io.opencola.core.serialization.ByteArrayCodec
import java.nio.ByteBuffer

object IntByteArrayCodec : ByteArrayCodec<Int> {
    override fun encode(value: Int): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
    }

    override fun decode(value: ByteArray): Int {
        return ByteBuffer.wrap(value).int
    }
}