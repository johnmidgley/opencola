package io.opencola.security

import io.opencola.util.Base58
import java.security.PrivateKey

class Signature(val algorithm: String, val bytes: ByteArray) {
    override fun toString(): String {
        return Base58.encode(bytes)
    }

    companion object {
        fun of(privateKey: PrivateKey, bytes: ByteArray): Signature {
            return sign(privateKey, bytes)
        }
    }
}