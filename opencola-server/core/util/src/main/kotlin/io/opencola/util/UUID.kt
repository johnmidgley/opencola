package io.opencola.util

import io.opencola.util.protobuf.Util as Proto
import java.nio.ByteBuffer
import java.util.*

fun UUID.toByteArray(): ByteArray {
    return ByteBuffer.allocate(Long.SIZE_BYTES * 2)
        .putLong(this.mostSignificantBits)
        .putLong(this.leastSignificantBits)
        .array()
}

fun UUID.toProto() : Proto.UUID {
    return Proto.UUID.newBuilder()
        .setMostSignificantBits(this.mostSignificantBits)
        .setLeastSignificantBits(this.leastSignificantBits)
        .build()
}

fun Proto.UUID.toUUID() : UUID {
    return UUID(this.mostSignificantBits, this.leastSignificantBits)
}

fun parseProto(bytes: ByteArray): Proto.UUID {
    return Proto.UUID.parseFrom(bytes)
}

fun decodeProto(bytes: ByteArray): UUID {
    return parseProto(bytes).toUUID()
}