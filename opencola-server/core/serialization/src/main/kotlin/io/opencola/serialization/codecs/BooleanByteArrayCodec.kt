package io.opencola.serialization.codecs

import io.opencola.serialization.ByteArrayCodec
import java.nio.ByteBuffer

object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
    // TODO: Can these be bytes instead?
    private const val FALSE = 0
    private const val TRUE = 1

    override fun encode(value: Boolean): ByteArray {
        return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(if (value) TRUE else FALSE).array()
    }

    override fun decode(value: ByteArray): Boolean {
        return ByteBuffer.wrap(value).int == TRUE
    }
}

// TODO: After DB migration, if ByteArrayCodecs are still used, switch to this one.
//  Legacy DBs have full int valued booleans, so can't change now.

//object BooleanByteArrayCodec : ByteArrayCodec<Boolean> {
//    private val FALSE = toByteArray(0.toByte())
//    private val TRUE = toByteArray(1.toByte())
//
//    fun toByteArray(value: Byte): ByteArray {
//        return ByteBuffer.allocate(1).put(0, value).array()
//    }
//
//    override fun encode(value: Boolean): ByteArray {
//        return if (value) TRUE else FALSE
//    }
//
//    override fun decode(value: ByteArray): Boolean {
//        require(value.size == 1)
//        return value[0] == TRUE[0]
//    }
//}