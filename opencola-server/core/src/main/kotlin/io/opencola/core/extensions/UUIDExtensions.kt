package io.opencola.core.extensions

import java.nio.ByteBuffer
import java.util.*

fun UUID.toByteArray(): ByteArray {
    return ByteBuffer.allocate(Long.SIZE_BYTES * 2)
        .putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits)
        .array()
}