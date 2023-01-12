package io.opencola.core.serialization

import java.nio.ByteBuffer

object LongByteArrayCodec : ByteArrayCodec<Long> {
    override fun encode(value: Long): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array()
    }

    override fun decode(value: ByteArray): Long {
        return ByteBuffer.wrap(value).long
    }
}