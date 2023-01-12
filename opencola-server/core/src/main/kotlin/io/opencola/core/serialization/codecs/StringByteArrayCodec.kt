package io.opencola.core.serialization.codecs

import io.opencola.core.serialization.ByteArrayCodec

object StringByteArrayCodec : ByteArrayCodec<String> {
    override fun encode(value: String): ByteArray {
        return value.toByteArray()
    }

    override fun decode(value: ByteArray): String {
        return String(value)
    }
}