package io.opencola.serialization.codecs

import io.opencola.serialization.ByteArrayCodec

object BytesByteArrayCodec : ByteArrayCodec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray {
        return value
    }

    override fun decode(value: ByteArray): ByteArray {
        return value
    }

}