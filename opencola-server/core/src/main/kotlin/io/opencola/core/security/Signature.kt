package io.opencola.core.security

import io.opencola.util.Base58
import java.security.PrivateKey

class Signature(val bytes: ByteArray) {
    override fun toString(): String {
        return Base58.encode(bytes)
    }

    companion object {
        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return Signature(sign(privateKey, bytes))
        }
    }
}