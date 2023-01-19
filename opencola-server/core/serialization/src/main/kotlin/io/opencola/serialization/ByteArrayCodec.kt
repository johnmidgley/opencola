package io.opencola.serialization

// TODO: Should this be ValueCodec?
// TODO - change to ByteArraySerializer?
interface ByteArrayCodec<T> {
    fun encode(value: T): ByteArray
    fun decode(value: ByteArray): T
}

