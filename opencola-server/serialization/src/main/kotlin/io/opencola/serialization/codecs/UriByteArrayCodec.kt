package io.opencola.serialization.codecs

import io.opencola.serialization.ByteArrayCodec
import java.net.URI

object UriByteArrayCodec : ByteArrayCodec<URI> {
    override fun encode(value: URI): ByteArray {
        return value.toString().toByteArray()
    }

    override fun decode(value: ByteArray): URI {
        return URI(String(value))
    }
}