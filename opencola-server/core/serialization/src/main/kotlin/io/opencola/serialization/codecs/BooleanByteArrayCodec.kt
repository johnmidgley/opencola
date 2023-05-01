package io.opencola.serialization.codecs

import io.opencola.serialization.ByteArrayCodec
import java.nio.ByteBuffer

object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
    private val FALSE = toByteArray(0.toByte())
    private val TRUE = toByteArray(1.toByte())

    fun toByteArray(value: Byte): ByteArray {
        return ByteBuffer.allocate(1).put(0, value).array()
    }

    override fun encode(value: Boolean): ByteArray {
        return if (value) TRUE else FALSE
    }

    override fun decode(value: ByteArray): Boolean {
        require(value.size == 1)
        return value[0] == TRUE[0]
    }
}