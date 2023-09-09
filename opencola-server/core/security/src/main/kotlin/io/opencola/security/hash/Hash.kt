package io.opencola.security.hash

import io.opencola.util.toHexString

abstract class Hash(val bytes: ByteArray) {
    fun toHexString(): String = bytes.toHexString()
    override fun toString(): String = toHexString()

    override fun hashCode(): Int {
        return bytes.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is Hash && bytes.contentEquals(other.bytes)
    }
}