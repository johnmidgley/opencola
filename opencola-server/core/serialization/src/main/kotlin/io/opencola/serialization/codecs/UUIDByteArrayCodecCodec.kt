package io.opencola.serialization.codecs

import io.opencola.util.toByteArray
import io.opencola.serialization.ByteArrayCodec
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.*

object UUIDByteArrayCodecCodec : ByteArrayCodec<UUID> {
    override fun encode(value: UUID): ByteArray {
        return value.toByteArray()
    }

    override fun decode(value: ByteArray): UUID {
        return ByteArrayInputStream(value).use {
            UUID(
                ByteBuffer.wrap(it.readNBytes(Long.SIZE_BYTES)).long,
                ByteBuffer.wrap(it.readNBytes(Long.SIZE_BYTES)).long
            )
        }
    }
}