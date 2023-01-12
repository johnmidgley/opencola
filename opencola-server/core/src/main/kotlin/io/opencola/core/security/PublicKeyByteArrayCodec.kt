package io.opencola.core.security

import io.opencola.core.serialization.ByteArrayCodec
import java.security.PublicKey

object PublicKeyByteArrayCodec : ByteArrayCodec<PublicKey> {
    override fun encode(value: PublicKey): ByteArray {
        return value.encoded
    }

    override fun decode(value: ByteArray): PublicKey {
        return publicKeyFromBytes(value)
    }
}