package opencola.core.model

interface ByteArrayCodec {
    fun encode(value: Any?): ByteArray
    fun decode(value: ByteArray?): Any?
}