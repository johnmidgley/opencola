package io.opencola.security.hash

import io.opencola.util.hexStringToByteArray
import java.security.MessageDigest

class Sha256Hash private constructor(bytes: ByteArray) : Hash(bytes) {
    companion object {
        fun fromBytes(bytes: ByteArray): Sha256Hash {
            require(bytes.size == 32)
            return Sha256Hash(bytes)
        }

        fun fromHexString(hashHexString: String): Sha256Hash {
            return hashHexString
                .hexStringToByteArray()
                .also { require(it.size == 32) }
                .let { Sha256Hash(it) }
        }

        fun ofBytes(bytes: ByteArray): Sha256Hash {
            return Sha256Hash(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes)
            )
        }

        fun ofString(input: String): Sha256Hash {
            return ofBytes(input.toByteArray())
        }
    }
}