package io.opencola.security.hash

import io.opencola.util.toHexString

abstract class Hash(val bytes: ByteArray) {
    fun toHexString(): String = bytes.toHexString()
    override fun toString(): String = toHexString()
}